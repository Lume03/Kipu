# Auditoría de calidad de Kipu — 2026-11-11

Auditoría estática de solo lectura sobre `main` (commit `2edc52bc`), siguiendo las skills `ponytail-audit` y `ponytail`. Nadie editó código, ejecutó Gradle, usó dispositivos ni tocó Supabase (ni remoto ni local). Se leyeron unas 140.000 líneas: Kotlin de `app/src` (main y tests), las 116 migraciones SQL con sus tests y las 9 edge functions.

**Carga asumida:** miles de usuarios, uno o dos dispositivos cada uno, miles de movimientos por usuario y uso offline frecuente (outbox con decenas de comandos al reconectar).

**Cómo se hizo:** 31 porciones de 2.000–7.000 líneas, cada una con su lista exacta de archivos y sin solapes (se comprobó con un script), más dos barridos (H1 deuda y H2 basura). Los modelos se eligieron por riesgo: Codex Sol para dinero, sync, seguridad, billing y SQL; Terra o Sonnet para lógica de datos normal; Gemini Flash para UI. A media auditoría Codex agotó la cuota y MOV2, ACC2, MOV3, DEB2, DB1, REC1, CAP2 y BUD1 pasaron a Claude (Opus o Sonnet). Cinco informes de Codex Terra (MOV4, ACC3, CAT1, CAP1, GOA1) llegaron parciales por lecturas truncadas y se completaron con una segunda pasada. Después hubo tres pasadas de refuerzo con Codex: GOA1 se releyó entero, CAP2 completó su presentación y CAP1 tuvo una pasada dirigida al listener de notificaciones. Los informes de cada porción están en este mismo directorio (`<id>.md`).

**Verificación de Fable:** abrí el código de los 8 críticos y de 49 de los 102 altos (todos los de Gemini, que eran los menos fiables, y una muestra de los de Codex, Opus y Sonnet), además de los de confianza baja que llevan a borrar código. Resultado: 1 falso positivo (UI3-01; GOA1-04 lo descartó su propio worker), 4 duplicados entre capas, 4 críticos rebajados a alto (DEB1-01, GOA1-09, UI2-01 y UI2-02) y 22 altos rebajados (18 a medio y 4 a bajo). De esos 26 cambios de severidad, 21 son de Gemini. Los hallazgos que no abrí figuran como «probable» con la confianza que les dio su worker.

## 1. Resumen ejecutivo

### Top 10 riesgos

1. **La corrección del saldo inicial suma dinero en el servidor** (SQL1-01, crítica, verificado; con SQL1-02 y SQL1-03). Al corregir dos veces el saldo inicial de una cuenta, `record_opening_adjustment_v1` revierte siempre la apertura original y no el ajuste vigente. Apertura 100, corrección a 200 y otra a 300: el servidor contabiliza 400 y el móvil 300. Además la corrección no se publica en el feed (otro dispositivo sigue viendo 100) y un reintento tras perder la respuesta falla por clave duplicada. Esto es dinero mal contado en el libro mayor del servidor.
2. **Un reembolso de compra con tarjeta bloquea la sincronización de ese teléfono para siempre** (MOV2-01, crítica, verificado). La hidratación rechaza el reembolso al bajarlo del servidor, porque trae o le falta `card_id` según qué versión del pull esté desplegada. Rechazar un cambio revierte la página entera y el checkpoint no avanza. Desde ese momento el dispositivo no recibe nada más: ni movimientos ni cuentas de otros dispositivos.
3. **Anular una compra que ya tuvo un reembolso deja el abono vivo, sin forma de corregirlo** (SQL4-01 = MOV1-01, crítica, verificado). Ni el servidor ni el cliente impiden anular el original. Al reembolso no se le puede anular porque está protegido, así que la cuenta queda inflada en el importe reembolsado.
4. **Un comando fallido congela la cola de sync de su entidad, o de todo el módulo** (patrón repetido en ACC5-03, ACC5-04, BUD1-02, REC1-01, DEB1-04, MOV2-07, MOV2-08, CAT1-02 y UI2-01). Un rechazo, un 401 tomado como permanente, un conflicto o un payload ilegible deja los comandos siguientes esperando sin salida, o reintentando cada 160 s indefinidamente. El usuario ve sus cambios guardados en el teléfono pero nunca llegan al servidor.
5. **Las ediciones offline se envían con una revisión vieja o en desorden** (ACC5-02, MOV2-02, MOV2-03, CAT1-03, CAT1-07, CAT2-02, GOA1-01; en servidor SQL1-05, SQL1-06 y SQL4-04). Dos ediciones seguidas sin conexión mandan la misma revisión base, así que la segunda acaba en un conflicto falso. Una edición puede llegar antes que el alta y perderse. Y un cambio remoto que se saltó mientras había un comando pendiente no se vuelve a bajar nunca.
6. **Deudas y metas: comandos enviados con la sesión de otra cuenta, conflictos leídos como fallo de red y una condonación que bloquea su propio eco** (DEB1-01, GOA1-09, DEB1-02 y DEB1-03). DEB1-01 y GOA1-09: si cambias de cuenta en el mismo teléfono mientras corre el worker, una deuda o una meta de A se puede crear en B. Categorías tiene el mismo hueco (§2). DEB1-03: los conflictos reales del servidor se reintentan indefinidamente como si fueran de red. DEB1-02: un ajuste o una condonación hechos offline detienen el feed de deudas de ese usuario.
7. **Billing** (BIL1-01, BIL1-03, BIL1-04, BIL1-05 y BIL1-06, altos). El servidor no comprueba `obfuscatedAccountId`, así que la primera verificación puede asignar la compra a otra cuenta Kipu. Si el usuario sale de la pantalla mientras paga, la compra se pierde hasta que vuelva a abrir Planes, y Play reembolsa lo que no se reconoce en 3 días. La edge function `plans` rechaza el cupo 5+5 de categorías ya aprobado. El refresco periódico puede quitar Premium durante el periodo de gracia.
8. **Privacidad con la app bloqueada o en modo privado** (SEC1-01, UI3-02, MOV4-04, ACC3-03, DEB2-05, CAP1-02 y ACC2-01). No hay `FLAG_SECURE`, así que la vista de Recientes muestra saldos. Los widgets muestran datos con la app bloqueada. Varias pantallas enseñan importes con «ocultar saldos» activado. Al descartar una captura se conserva el texto de la notificación. «Editar tarjeta» permite guardar el número completo de la tarjeta como alias, y ese alias se sincroniza. Además el lock biométrico no funciona en Android 9 y 10 (SEC1-02).
9. **Rechazos del servidor que dejan efectos locales** (ACC5-05, ACC5-06, GOA1-05 y CAP1-04). Una cuenta cuya creación rechazó el servidor sigue en el teléfono con su saldo, una compra rechazada sigue publicada en el historial de la tarjeta y un traslado rechazado sigue moviendo dinero entre metas. Al capturar, elegir otra cuenta cambia la moneda sin convertir el monto.
10. **Fechas, calendario e importes de entrada** (MOV3-01, REC1-02, BUD1-01, ACC3-02, UI2-02). El editor de movimientos guarda la medianoche UTC, que en Lima cae el día anterior. El recordatorio de una recurrencia suena una sola vez en su vida. Un cambio de calendario pendiente se pierde en presupuestos de rango personalizado. El filtro «este ciclo» de la tarjeta usa 30 días en vez de la fecha de corte. En el setup inicial, un saldo escrito con coma decimal («150,50») se guarda multiplicado por 100.

### Métricas

| Severidad | Reportado por workers | Tras verificación (sin duplicados ni falsos positivos) |
|---|---|---|
| crítica | 8 | 3 |
| alta | 102 | 80 |
| media | 191 | 209 |
| baja | 110 | 113 |
| total | 411 | 405 |

| Módulo | Porciones | Crítica | Alta | Media | Baja | Total |
|---|---|---|---|---|---|---|
| Movimientos | MOV1, MOV2, MOV3, MOV4, MOV5 | 1 | 7 | 37 | 24 | 69 |
| Cuentas y tarjetas | ACC1, ACC5, ACC2, ACC3, ACC4 | 0 | 12 | 36 | 13 | 61 |
| Backend SQL (migraciones) | SQL1, SQL2, SQL3, SQL4 | 2 | 14 | 19 | 0 | 35 |
| Deudas | DEB1, DEB2 | 0 | 5 | 13 | 10 | 28 |
| Seguridad y auth | SEC1, SEC2 | 0 | 3 | 17 | 7 | 27 |
| Billing y planes | BIL1 | 0 | 6 | 3 | 0 | 9 |
| Categorías y comercios | CAT1, CAT2 | 0 | 8 | 14 | 8 | 30 |
| Metas | GOA1 | 0 | 8 | 5 | 0 | 13 |
| Presupuestos | BUD1 | 0 | 2 | 5 | 9 | 16 |
| Recurrencias y calendario | REC1 | 0 | 2 | 8 | 6 | 16 |
| Captura e importación | CAP1, CAP2 | 0 | 5 | 12 | 9 | 26 |
| Base de datos local | DB1 | 0 | 0 | 6 | 7 | 13 |
| UI ligera (home, analytics, settings, notificaciones, widgets, portabilidad, navegación) | UI1, UI2, UI3, NAV1 | 0 | 8 | 34 | 20 | 62 |

| Categoría | Hallazgos |
|---|---|
| bug | 180 |
| datos | 46 |
| test-faltante | 39 |
| borrar | 38 |
| concurrencia | 30 |
| rendimiento | 22 |
| seguridad | 17 |
| escala | 16 |
| fusionar | 14 |
| dividir | 3 |

| Estado de verificación | Hallazgos |
|---|---|
| probable | 362 |
| verificado | 43 |
| duplicado | 4 |
| falso positivo | 2 |

Por debajo de las cifras:
- Los críticos se concentran en el contrato entre app y servidor del dinero (aperturas y reembolsos) y en el pull compartido.
- Casi la mitad de los altos son el mismo par de patrones de sync, repetido en cada módulo (puntos 4 y 5 del top 10). Arreglar el patrón una vez vale más que arreglar 15 hallazgos sueltos (ver §2).
- La UI tiene muchos medios y bajos de copia, foco y estado, pero pocos riesgos de datos.

## 2. Cruce dentro de cada módulo y entre módulos

### Fronteras domain ↔ data ↔ UI
- **Movimientos.** El reembolso tiene tres reglas distintas:
  - El dominio (`RegisterRefundTransaction.kt:95-108`) y el data source (`MovementLocalDataSource.kt:1540-1550`) usan listas de estados diferentes. Por eso un gasto ACTIVE sincronizado no admite reembolso (MOV1-02), mientras uno CONFIRMED local sí.
  - El servidor (SQL4-01) tampoco protege la compra original.
  - El hidratador (MOV2-01) no reconoce el reembolso con tarjeta.

  Conviene una sola función de elegibilidad en domain, usada por los dos lados, y un caso de test que recorra el ciclo completo: registrar, sincronizar, bajar y anular.
- **Cuentas.** La UI acepta alias que el dominio rechaza con una excepción (ACC2-02, que cuelga la app) y alias que el dominio debería rechazar, como un número de tarjeta completo (ACC2-01). La validación debería vivir en un solo sitio (domain) y que la UI muestre su error, sin repetirla.
- **Deudas.** La vista previa del cronograma (DEB2-03) reparte los céntimos distinto que `SetDebtSchedule` (DEB1-07), así que el usuario ve un plan y se guarda otro. La UI debe pedirle el plan al caso de uso en lugar de recalcularlo.
- **Categorías.** El editor de presentación envía la revisión de la categoría y no la de su presentación (CAT2-02). El servidor no bloquea ni deduplica esa edición (SQL1-05, SQL1-06), y al resolver un conflicto descarta la versión elegida (SQL1-04). Además la pantalla de conflictos no tiene ruta (CAT2-07). Hoy los conflictos de categorías no se pueden resolver bien de punta a punta.

### Entre módulos
- **Reglas de dinero.** Todo el dinero es `Long` en céntimos y el modelo `Money` tiene un tope. Pero:
  - El parser de deudas (DEB1-06) no aplica ese tope y deja pasar importes que luego hacen fallar al constructor de `Money`.
  - El saldo pendiente de las cuotas ignora el interés (ACC1-01), mientras deudas sí lo maneja. Falta decidir si la deuda de una tarjeta incluye intereses.
  - No encontré `Double` en los cálculos de dinero del dominio. Los redondeos de cuotas están en `core/finance`; DEB1 los revisó y pasan sus tests contra cálculo decimal.
- **Contratos app ↔ edge function ↔ SQL que no coinciden:**
  - La contraseña Unicode (SEC1-03).
  - El cupo 5+5 de categorías: el SQL lo admite y la edge function `plans` no (BIL1-04).
  - El `error` de las RPC de deudas: el SQL devuelve un objeto y el DTO espera texto (DEB1-03).
  - El `card_id` de los reembolsos en el pull (MOV2-01).
  - La fecha de las condonaciones: el SQL la fija al procesar y la app al registrar (DEB1-02).
  - Las aperturas corregidas (SQL1-01 y SQL1-02).

  Todos salen de que cada lado se escribe sin un fixture compartido. Una medida barata: guardar respuestas reales de las RPC como JSON en `app/src/test/resources` y deserializarlas en los tests de cada `*Api`.
- **Sync: seis outbox casi iguales con defectos distintos.** Movimientos, cuentas, deudas, metas, presupuestos, categorías y recurrencias tienen cada uno su worker, su DAO de outbox, sus estados y su `scheduleSync`. Los mismos fallos aparecen con distinta forma:
  - Un 401 tratado como permanente (ACC5-04), mientras deudas y movimientos lo reintentan.
  - `KEEP` que pierde el aviso del último comando (ACC5-08, CAT1-02).
  - `REPLACE` que cancela una sincronización en curso (REC1-03).
  - Un tope de 10 comandos con backoff exponencial (MOV2-05).
  - Un FAILED o CONFLICT que bloquea a los siguientes (punto 4 del top 10).
  - Cada hidratador vuelve a recorrer el feed compartido completo con su propio checkpoint: 5 o 6 descargas del feed en una instalación nueva (dudas de MOV2).

  Propuesta mínima, sin un framework nuevo:
  - Una sola política escrita, con una función compartida en `core/session` o `core/sync` para tres cosas: clasificar la respuesta HTTP en reintentable, permanente o conflicto; decidir qué pasa con los sucesores de un comando que falló; y fijar la política de programación (`APPEND_OR_REPLACE`).
  - Aplicar esa función en cada worker.
  - El pull compartido se puede hacer una sola vez y repartir por tipo, pero eso es un cambio de arquitectura y requiere decisión.
- **Comandos con la sesión de otra cuenta.** Cuentas, movimientos, presupuestos, notificaciones y recurrencias comparan el dueño del comando con `session.userId` antes de enviar. Deudas (DEB1-01), metas (GOA1-09) y categorías no lo hacen: `CategoriesApi.getAuthHeader()` (`CategoriesApi.kt:30-33`) toma la sesión vigente sin comprobar el dueño. Lo de categorías lo encontré al cruzar los módulos y no tiene ID propio de worker. El arreglo es el mismo en los tres: pasar el `userId` del comando y rechazar el envío si no coincide, como ya hace `FinancialInstrumentsApi`.
- **Revisiones.** Varios repositorios guardan `remoteRevision` al encolar y no la actualizan cuando el comando anterior se confirma (ACC5-02, CAT1-07, GOA1-01). Deudas y movimientos encadenan mejor. Conviene copiar el patrón de identidad de débito de ACC5 (persistir la revisión confirmada del comando anterior en el payload del siguiente).
- **Idempotencia del cliente.** Al reintentar un guardado fallido, Quick (MOV3-05) y las pantallas de deudas (DEB2-09) generan una clave nueva. Si el fallo ocurrió después del commit local, eso duplica el movimiento o la deuda. La clave tiene que nacer con el borrador, no con el intento.
- **Privacidad.** El enmascarado de saldos (`LocalBalanceMasked`) se aplica pantalla por pantalla y se olvida en varias (MOV4-04, ACC3-03, DEB2-05). `ui/component/MoneyText.kt` ya lee `LocalBalanceMasked.current` por defecto. Las pantallas que fallan formatean el importe a mano en textos o diálogos, así que el arreglo es usar `MoneyText` o su formateador enmascarado en esos sitios, no crear nada nuevo.
- **Dinero en `Double`.** Solo aparece en porcentajes de utilización de la tarjeta (`CreditCalculations.kt:86`, `ObserveInstrumentsUseCases.kt:121`), que no se guardan. Los importes son `Long` en céntimos en todas las capas revisadas.


## 3. Hallazgos por módulo

Cada módulo une los informes de sus porciones, ordenados por severidad. «Verificado» = Fable abrió el código y confirmó el escenario. «Probable» = no reabierto; se apoya en la lectura del worker (se indica su confianza). Los duplicados y falsos positivos se listan al final del informe y no cuentan en las métricas.

### Movimientos

Porciones: MOV1 (Codex gpt-6.1-sol high); MOV2 (Claude opus medium (relevo de Codex)); MOV3 (Claude sonnet medium (relevo)); MOV4 (Codex gpt-6.1-terra medium (2 pasadas)); MOV5 (Gemini 3.8 flash-high). Hallazgos: 69 (1 crítica, 7 alta, 37 media, 24 baja).

#### [MOV2-01] El reembolso de una compra con tarjeta bloquea para siempre el pull del dispositivo
- **Severidad:** crítica · **Categoría:** bug · **Confianza del worker:** media · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt:368-370 y :395-397
- **Qué está mal:** un reembolso local de compra con tarjeta se guarda con `cardId = purchase.cardId` y `operationKind = "REFUND"` (MovementLocalDataSource.kt:156-157). Cuando llega del feed, la hidratación lo trata como movimiento normal. Con el payload crudo del RPC (`'card_id', v_purchase.card_id`, 20261024182114_ep_mov_refund_v1.sql) la línea 368 lo rechaza porque un movimiento que no es de tarjeta trae `card_id`. Con la proyección de 20261108173837 (`planning_transaction_sync_projection_v1`, sin `card_id`), la línea 395 lo rechaza en el dispositivo que lo creó porque `existing.cardId != remote.cardId (null)`. En los dos casos se lanza `InvalidSyncPage`, la página entera se deshace y el checkpoint no avanza.
- **Escenario de fallo:** el usuario registra un reembolso de S/ 40 de una compra con tarjeta y sincroniza. En el pull siguiente esa página falla. Desde entonces `pullChanges` devuelve `false` en cada ejecución: el dispositivo ya no recibe movimientos, cuentas ni tarjetas de otros dispositivos, el worker queda en `Result.retry()` sin fin y el saldo se desvía en silencio. Con el payload crudo también falla la restauración en un dispositivo nuevo.
- **Fix mínimo:** permitir `cardId` en los reembolsos y no exigir que el `cardId` local y el remoto coincidan cuando `remoteKind == "REFUND"`. Por ejemplo, en 368: `else if ((remote.cardId != null && !remoteKind.equals("REFUND", true)) || ...)`; en 395: comparar `cardId` solo si `isCardPurchase || isCardPayment`. Añadir una prueba de pull de reembolso con tarjeta, con el dispositivo de origen y con uno nuevo.
- **Verificación (Fable):** Confirmado en `MovementHydration.kt:368` (payload crudo con `card_id`) y `:395` (proyección final sin `card_id`); `false` lanza `InvalidSyncPage` y el checkpoint no avanza. Despliegue comprobado el 2026-11-11 (`list_migrations`: las 116 migraciones del repo están aplicadas, incluida 20261108173837): el feed usa la proyección sin `card_id`, así que se bloquea el dispositivo que creó el reembolso; un dispositivo nuevo restaura bien.

#### [MOV1-02] Un gasto estándar sincronizado sigue sin admitir reembolso
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/domain/RegisterRefundTransaction.kt:105-108; app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt:454,1545-1547
- **Qué está mal:** El registro crea gastos estándar ACTIVE y ambas comprobaciones de elegibilidad exigen CONFIRMED o REVISED. ACTIVE también es la representación que el feed histórico usa para movimientos confirmados; SYNCED no cambia esa comprobación.
- **Escenario de fallo:** Registrar gasto estándar, sincronizarlo correctamente y abrir su detalle → ACTIVE/SYNCED no ofrece reembolso y el caso de uso devuelve PURCHASE_NOT_ELIGIBLE. Un gasto igual pasado primero por edición sí queda REVISED y admite el reembolso. Los tests de reembolso siembran compras CONFIRMED y ocultan esa diferencia.
- **Fix mínimo:** Alinear ambas comprobaciones con la representación confirmada real: admitir ACTIVE cuando exista evidencia de aceptación, por ejemplo SYNCED, sin habilitar indiscriminadamente gastos pendientes. Añadir un caso Room de registro → aceptación → reembolso, sin sembrar a mano CONFIRMED.
- **Verificación (Fable):** Confirmado: el dominio (`RegisterRefundTransaction.kt:105`) y el data source (`:1545`) excluyen ACTIVE para gastos estándar, y el registro y el pull los dejan ACTIVE.

#### [MOV2-02] Los comandos del mismo movimiento se envían desordenados; una edición puede perderse
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:156-160, :342-384 (la consulta está en MovementDao.kt:191-203)
- **Qué está mal:** el worker reclama comandos por `created_at` sin mirar si queda pendiente uno anterior del mismo `aggregate_id`. Si el alta está en backoff (`ACCOUNT_SYNC_PENDING`, 5xx o 429), la edición posterior del mismo movimiento se envía igual. La guarda `dependsOnCommandId` existe, pero ningún código de producción la rellena (ver MOV2-09).
- **Escenario de fallo:** offline, el usuario crea una cuenta Yape, registra un gasto con canal Yape y luego corrige el monto. Al reconectar, el alta vuelve con `ACCOUNT_SYNC_PENDING` (línea 288) y en la misma pasada se envía la edición. El servidor responde `REJECTED NOT_FOUND` (retryable=false) y `MovementRejectionResolver.rejectClaimed` revierte la edición local. Después el alta se sincroniza con el monto viejo y la corrección del usuario se pierde. Con dos ediciones offline, la segunda puede llegar antes que la primera y acabar en un `CONFLICT` falso.
- **Fix mínimo:** al inicio de `processRevisionCommand` (y en reembolso/registro si se quiere), `if (movementDao.getPendingOutboxForTransaction(cmd.userId, cmd.aggregateId).firstOrNull()?.id != cmd.id) { retryCommand(cmd, now, "WAITING_PREVIOUS_COMMAND"); return false }`. Sustituye a la rama `dependsOnCommandId` (MOV2-09).

#### [MOV2-03] Un cambio remoto sobre un movimiento con comando pendiente se salta y no se vuelve a aplicar
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** media · **Estado:** probable
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt:399-425
- **Qué está mal:** si el movimiento tiene un comando local pendiente, el pull devuelve `true` sin aplicar la revisión remota y el checkpoint avanza. Si luego ese comando termina en `CONFLICT`, nada vuelve a traer la revisión remota: el feed no la repite.
- **Escenario de fallo:** el dispositivo A edita offline un gasto de S/ 100 a S/ 80 (revisión local 2) y el dispositivo B lo edita online a S/ 50 (revisión 2 en el servidor). A reconecta con más de 10 comandos pendientes, o con ese comando en backoff, y el pull ve el cambio con `revision <= existing.revision` y lo salta (línea 400). Después el comando de A recibe `CONFLICT`. A muestra S/ 80 y su saldo, el servidor tiene S/ 50, y la diferencia queda así hasta que alguien vuelva a editar ese movimiento.
- **Fix mínimo:** cuando el worker marca un comando de revisión como `CONFLICT` (SyncMovementsWorker.kt:414-443), volver a traer ese movimiento. La forma más pequeña: bajar el checkpoint a 0 para la próxima pasada, ya que los conflictos son raros y el pull es idempotente. Otra opción es guardar la secuencia saltada y rebobinar a ella.

#### [MOV3-01] El editor guarda la fecha elegida como medianoche UTC (día y hora incorrectos en Lima)
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/MovementEditorSheet.kt:771-801 (también 1054-1065)
- **Qué está mal:** el `DatePicker` de Material3 trabaja en milisegundos de medianoche UTC. El editor le pasa `occurredAt` tal cual como `initialSelectedDateMillis` (línea 773) y devuelve `selectedDateMillis` tal cual a `onDateChanged` (780-782). No convierte entre UTC y zona local, y pierde la hora del día. El registro rápido sí lo hace bien (`toUtcDateMillis` / `datePickerMillisToLocalInstant`, QuickMovementScreen.kt:1687-1698) y el reembolso también. Es la misma regla aplicada distinto en dos sitios.
- **Escenario de fallo:** gasto del 15-nov a las 20:00 Lima (= 16-nov 01:00Z). Al abrir el selector se marca el 16. Si el usuario solo pulsa "Aceptar" sin tocar nada, `onDateChanged(16-nov 00:00Z)` marca el campo como modificado y, al guardar, el movimiento pasa a 15-nov 19:00 Lima, sin hora real. Si elige el 20-nov, se guarda 20-nov 00:00Z = 19-nov 19:00 Lima y la etiqueta (`editorDateLabel`, usa `systemDefault`) muestra "19 nov". El movimiento cae un día antes, y puede cambiar de periodo de presupuesto, de corte de tarjeta o de mes en analytics.
- **Fix mínimo:** copiar las dos funciones `toUtcDateMillis`/`datePickerMillisToLocalInstant` (ya existen en QuickMovementScreen.kt, hacerlas `internal` y reutilizarlas) en el `DatePickerDialog` del editor. Conserva la hora original.
- **Verificación (Fable):** `onDateChanged(dateState.selectedDateMillis)` pasa la medianoche UTC del DatePicker sin convertir a Lima.

#### [MOV4-01] La comparación con el mes anterior no hace nada
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt:710 y app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFilterDraft.kt:45
- **Qué está mal:** El interruptor cambia `comparePreviousMonth`, pero la validación no lo incluye en el estado aplicado y el historial no recibe ninguna comparación.
- **Escenario de fallo:** El usuario activa «Comparar con mes anterior» y aplica: ve el historial normal, sin comparación; al reabrir el filtro el interruptor vuelve a apagarse.
- **Fix mínimo:** Implementar la comparación hasta su resultado visible, o retirar el interruptor hasta que exista dicha funcionalidad.

#### [MOV4-04] La confirmación de eliminación revela el importe oculto
- **Severidad:** alta · **Categoría:** seguridad · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryDelete.kt:103
- **Qué está mal:** El diálogo inserta el importe real en un Text sin consultar la preferencia que oculta saldos. El importe también queda en las semánticas accesibles.
- **Escenario de fallo:** Con «Ocultar importes» activo, deslizar una operación y abrir la confirmación muestra su cantidad a quien mire la pantalla o use el lector de pantalla.
- **Fix mínimo:** Mostrar el importe mediante MoneyText, que respeta el estado de privacidad, y comprobar también sus semánticas.

#### [MOV4-05] La oferta fija un precio y un descuento sin consultar Google Play
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt:1794
- **Qué está mal:** El anuncio siempre afirma 40 % de ahorro y S/ 59.90 por año, mientras la pantalla de compra obtiene el precio localizado de Play.
- **Escenario de fallo:** Si Play ofrece otro precio, moneda o promoción, el usuario ve una cifra en el anuncio y otra al abrir la compra.
- **Fix mínimo:** Retirar precio, equivalencia y descuento del anuncio o mostrar los datos reales de la oferta cargada.
- **Verificación (Fable):** «AHORRA 40%» literal en `MovementFiltersSheet.kt:1794`.

#### [MOV1-04] Un rechazo antiguo marca para siempre la operación vigente como fallida
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt:1415-1417; app/src/main/java/com/kipu/app/feature/movements/data/local/MovementRejectionResolver.kt:101-107,141-145
- **Qué está mal:** El rechazo de una corrección restaura el movimiento oficial con SYNCED, pero conserva el comando rechazado como FAILED_PERMANENT. `withOutboxStatus` convierte cualquier fallo histórico de ese movimiento en fallo de su estado actual, incluso después de otra corrección aceptada.
- **Escenario de fallo:** Gasto confirmado de S/100, edición a S/90 rechazada y compensada → la fila vuelve a S/100/SYNCED; consultar por MovementLocalDataSource devuelve FAILED_PERMANENT por el intento antiguo. Una nueva edición exitosa sigue apareciendo fallida hasta descartar expresamente la evidencia vieja.
- **Fix mínimo:** Hacer que el estado derivado mire solo comandos que todavía afectan a la cabeza actual, excluyendo los ya compensados mediante evidencia de reconciliación. Añadir un caso de rechazo compensado seguido por lectura y nueva aceptación.

#### [MOV1-05] Cada actualización del historial ejecuta una consulta por movimiento
- **Severidad:** media · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt:34-39,286-287,1415-1417; app/src/main/java/com/kipu/app/feature/movements/data/local/MovementDao.kt:152-153
- **Qué está mal:** `withOutboxStatus` carga todos los comandos, incluidos sus payloads JSON, individualmente para cada fila. El flujo de historial completo repite ese trabajo en cada invalidación de transactions; no solo carga los movimientos visibles.
- **Escenario de fallo:** Usuario con 5.000 movimientos registra o sincroniza uno → el observador completo vuelve a emitir y hace 5.000 consultas secuenciales adicionales, materializando también comandos antiguos. Una página de 200 filas añade otras 200 consultas a la consulta paginada.
- **Fix mínimo:** Resolver el estado necesario con EXISTS en la consulta original, o cargar en una sola consulta el conjunto de aggregate_id afectados y aplicarlo en memoria. No cargar payloads para decidir un estado.

#### [MOV1-06] Filtrar solo por moneda evita la comprobación Premium
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/domain/model/MovementHistoryQuery.kt:49-60,83-84; app/src/main/java/com/kipu/app/feature/movements/data/local/MovementQuerySqlBuilder.kt:148-150
- **Qué está mal:** `hasCriteria` omite `currency`, aunque SQL sí aplica ese campo como criterio avanzado. La política permite la consulta como básica y no ejecuta el fallback que elimina los filtros avanzados.
- **Escenario de fallo:** Usuario Free solicita MovementHistoryQuery(advancedCriteria = AdvancedHistoryCriteria(currency = "USD")) → recibe Allowed y solo movimientos USD, sin concesión Premium. Los tests de importes siempre añaden minAmountMinor y no detectan la consulta de moneda sola.
- **Fix mínimo:** Incluir `!currency.isNullOrBlank()` en hasCriteria. Añadir un test de QueryMovementHistory con solo moneda que compruebe PremiumRequired y fallback sin advancedCriteria.

#### [MOV1-07] El filtro de fallos históricos siempre devuelve vacío
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/local/MovementQuerySqlBuilder.kt:52,156; app/src/androidTest/java/com/kipu/app/feature/movements/data/local/MovementHistoryQueryTest.kt:424
- **Qué está mal:** El predicado inicial excluye FAILED incondicionalmente y el filtro LEGACY_FAILED añade status IN ('FAILED'). La conjunción es imposible. Ya existe un test que espera la fila FAILED: el código actual contradice esa aserción, comprobado por lectura, no por ejecución.
- **Escenario de fallo:** Hay un movimiento legacy FAILED y el usuario consulta exclusivamente LEGACY_FAILED → lista y conteo vacíos en vez del movimiento. El mismo fallo ocurre al combinar ese estado con otros.
- **Fix mínimo:** Distinguir los tombstones de registros rechazados que deben ocultarse de los fallos históricos que el filtro admite; no usar FAILED como exclusión universal si el filtro explícito pide esos registros. Mantener el test existente como regresión.

#### [MOV1-08] La edición permite añadir categoría a una transferencia
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/domain/MovementRevisionPlanner.kt:42-58
- **Qué está mal:** El registro prohíbe categorías en transferencias, pero el planificador de edición no. Una categoría GENERAL se convierte en referencia compatible con TRANSFER en collectReferences, así que supera también la comprobación de tipo. El contrato remoto rechaza esa combinación.
- **Escenario de fallo:** Transferencia estándar válida entre A y B; se manda una revisión que conserva sus cuentas y añade una categoría GENERAL activa → la revisión local se acepta y se encola, pero la RPC devuelve INVALID_REFERENCE por categoría en transferencia. Queda una corrección que nunca podía ser aceptada.
- **Fix mínimo:** Rechazar after.type == TRANSFER con categoryId != null, igual que RegisterTransactionValidator. Añadir un test del planificador para transferencia con categoría GENERAL.

#### [MOV1-09] Corregir un movimiento desactiva la advertencia de duplicado
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/local/MovementDao.kt:82-93; app/src/main/java/com/kipu/app/feature/movements/domain/RegisterTransaction.kt:23-35
- **Qué está mal:** La consulta de similitud solo admite ACTIVE. Movimientos CONFIRMED o REVISED tienen efecto financiero vigente, pero no se buscan. El fake de FreeDeduplicationTest filtra sin estado y por tanto no representa esta restricción del DAO.
- **Escenario de fallo:** Registrar un gasto S/20 a las 10:00, corregir únicamente su nota y volver a registrarlo a las 10:01 con la misma cuenta/importe/moneda → la fila REVISED queda fuera y se registra otro gasto sin advertencia.
- **Fix mínimo:** Buscar los tres estados financieros vigentes y seguir excluyendo VOIDED y FAILED. Añadir un caso Room con REVISED que compruebe la advertencia del caso de uso.

#### [MOV2-04] Las propuestas de conflicto creadas por la hidratación no se pueden ni descartar ni rehacer
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt:406-421
- **Qué está mal:** la hidratación guarda `commandId = pending.id` (UUID de la fila de outbox) y `proposedSnapshot = rev.newPayload` (formato `MovementRevisionSnapshotCodec`). El resto del sistema espera la clave de idempotencia y el payload `ReviseOrVoidTransactionRequestDto`: el worker en SyncMovementsWorker.kt:416/428 y `discardProposal`/`redoProposal` en MovementLocalDataSource.kt:1429 y :1451.
- **Escenario de fallo:** un pull ve una revisión remota más nueva mientras la edición local está en backoff y se crea la propuesta. Con «Rehacer» el usuario recibe siempre `INVALID_PROPOSAL_SNAPSHOT`. Con «Descartar», la propuesta se cierra pero el comando sigue pendiente (no se encuentra por `idempotencyKey`), se envía, el servidor responde `CONFLICT` y el worker crea una segunda propuesta para la misma edición.
- **Fix mínimo:** en 411 usar `commandId = pending.idempotencyKey` y en 414 `proposedSnapshot = pending.payload`, igual que el worker. Dos líneas.

#### [MOV2-05] Con más de 10 comandos pendientes, sincronizar tarda horas
- **Severidad:** media · **Categoría:** escala · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:156-160 y :176; scheduler en :58-76
- **Qué está mal:** cada ejecución envía como mucho 10 comandos y después devuelve `Result.retry()`. WorkManager aplica su backoff por defecto: exponencial desde 30 s, sin `setBackoffCriteria`. Cada lote también descarga el feed completo.
- **Escenario de fallo:** un usuario vuelve de una semana sin señal con 100 movimientos. Hacen falta 10 ejecuciones, con esperas de 30 s, 60 s, 120 s… hasta unos 4,3 h acumuladas antes de que suba el último. Mientras tanto, otros dispositivos no ven sus datos.
- **Fix mínimo:** seguir reclamando mientras haya comandos listos, cambiando `for (index in 0 until 10)` por `while (true)` que corte cuando `claim` devuelva `null`. Los comandos fallidos ya quedan en backoff y no se vuelven a reclamar en la misma pasada.

#### [MOV2-06] La reparación única del ledger descarga el feed entero dos veces en cada instalación nueva
- **Severidad:** media · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:164-173; MovementHydration.kt:140-192
- **Qué está mal:** la marca `ledger-net-20261026-v1` es por instalación y por usuario. En un dispositivo nuevo, el primer pull baja todo el feed y la reparación lo vuelve a bajar desde 0, abriendo una transacción Room por cada cambio TRANSACTION. Ahí no hay nada que reparar: el bug corregido solo afectaba a pulls anteriores al arreglo.
- **Escenario de fallo:** un usuario con 5 000 movimientos (y varios eventos por movimiento) inicia sesión en un teléfono nuevo y descarga y procesa el feed dos veces. Si la segunda pasada falla por red, se repite en cada ejecución.
- **Fix mínimo:** antes del primer `pullChanges`, si `movementDao.getSyncCheckpoint(targetUserId) == null`, guardar la marca como hecha (nada fue hidratado por el código antiguo). Unas 2 líneas. Cuando todos los usuarios activos hayan pasado la reparación, borrar `repairDuplicatedLedger`.
- **Ahorro estimado:** ~55 líneas cuando se borre

#### [MOV2-07] Si la cuenta Yape/Plin queda rechazada, el gasto con canal reintenta para siempre
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:281-291
- **Qué está mal:** el alta con `paymentChannel` espera mientras el último comando de instrumento de la cuenta no esté en `SYNCED`. Pero `FAILED_PERMANENT` y `CONFLICT` también son estados finales de la outbox de instrumentos.
- **Escenario de fallo:** el usuario crea offline una billetera afiliada que el servidor rechaza para siempre (por ejemplo, por cuota del plan) y registra gastos con ese canal. Cada gasto reintenta `ACCOUNT_SYNC_PENDING` cada 160 s, nunca se resuelve ni se muestra como rechazado, y el worker nunca termina en `success`.
- **Fix mínimo:** si algún comando está en `FAILED_PERMANENT` o `CONFLICT`, llamar a `MovementRejectionResolver(database, movementDao).rejectClaimed(cmd, "ACCOUNT_SYNC_FAILED")` dentro de `ownerTransaction`, y esperar solo con los demás estados.

#### [MOV2-08] Un payload ilegible se reintenta sin fin
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:187-193
- **Qué está mal:** cualquier excepción al procesar un comando, incluida `SerializationException` al decodificar un payload de un contrato viejo, termina en `retryCommand(..., "PARSE_ERROR")`. No hay límite de intentos.
- **Escenario de fallo:** un comando guardado con un formato anterior que ya no decodifica se reintenta cada 160 s para siempre. El movimiento queda «pendiente» sin aviso y `hasUnfinishedOutbox` mantiene el worker en `retry`.
- **Fix mínimo:** capturar `kotlinx.serialization.SerializationException` antes del `catch (e: Exception)` y rechazar el comando con `rejectClaimed(cmd, "PARSE_ERROR")`. La decodificación ocurre antes de cualquier llamada de red, así que es seguro.

#### [MOV2-10] La lista completa de movimientos hace una consulta por fila en cada emisión
- **Severidad:** media · **Categoría:** escala · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/OfflineFirstMovementRepository.kt:47-48 → MovementLocalDataSource.kt:35 y :1415-1417
- **Qué está mal:** `observeTransactions` emite todos los movimientos del usuario, y `withOutboxStatus` lanza una consulta `getAllOutboxForTransaction` por cada fila. `observeItems` además combina cinco flujos, así que cualquier cambio en categorías, comercios, cuentas o tarjetas vuelve a mapear la lista completa.
- **Escenario de fallo:** con 5 000 movimientos, cada inserción de un movimiento o del outbox lanza 5 000 consultas en `RoomReceiptCandidateStore` (que combina con `observeTransactions`) y en `ConfirmStatementImport`.
- **Fix mínimo:** en MOV1, leer los ids con outbox `FAILED_PERMANENT` en una sola consulta (`SELECT aggregate_id ... WHERE state='FAILED_PERMANENT'`) y marcar en memoria.
- **Cross-módulo:** sí → MOV1

#### [MOV3-02] Tras un `Conflict` al guardar, el editor borra lo que el usuario escribió y el mensaje de error
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/MovementEditorViewModel.kt:418-427 y 207-210, 248-283
- **Qué está mal:** en la rama `Conflict` se pone `hasConflict=true` y `generalError="Conflicto de sincronización..."` y luego se llama `loadTransaction`. Esa función empieza con `generalError = null` (línea 210) y vuelve a rellenar todo el formulario desde la cabecera, con `hasConflict = activeProposal != null`. El `Conflict` de `commitRevise` es `REVISION_CONFLICT` (MovementLocalDataSource.kt:750), que no crea ninguna propuesta; por tanto `hasConflict` vuelve a `false`.
- **Escenario de fallo:** dos dispositivos; el dispositivo B corrige el monto y su cambio llega por sync mientras A tiene el editor abierto. En A el usuario cambia la categoría y nota, pulsa Guardar: el formulario se recarga con los valores de B, sin mensaje y sin los cambios del usuario. El usuario cree que guardó o no entiende qué pasó.
- **Fix mínimo:** guardar el mensaje y mostrarlo después de recargar (por ejemplo `loadTransaction(id, errorAfterLoad = "...")`), o no tocar los campos editados y solo refrescar `expectedRevision`/valores iniciales.

#### [MOV3-03] El editor ofrece cuentas de otra moneda y el rechazo llega como código crudo
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** MovementEditorSheet.kt:659-678 (pickers con `uiState.availableAccounts` sin filtrar), MovementEditorViewModel.kt:171-173 y 428-435
- **Qué está mal:** el selector de origen/destino muestra todas las cuentas activas. El Planner rechaza cualquier cuenta cuya moneda difiera de la del movimiento (`INVALID_REFERENCE`, MovementRevisionPlanner.kt:47). El mensaje al usuario es literalmente `No se pudo guardar la corrección (INVALID_REFERENCE)`. Ningún código se traduce. En el registro rápido, en cambio, el selector sí filtra por moneda.
- **Escenario de fallo:** gasto en PEN, el usuario cambia la cuenta a "Dólares" y pulsa "Guardar 1 cambio" → ve "(INVALID_REFERENCE)" sin saber qué corregir. Lo mismo con `REFERENCE_NOT_ELIGIBLE`, `OPERATION_SPECIALIZED`, `IMMUTABLE_FINANCIAL_KIND`.
- **Fix mínimo:** filtrar `accounts = uiState.availableAccounts.filter { it.currency.name == uiState.currency }` en los dos `AccountPickerSheet`, y mapear 3-4 códigos comunes a texto en español (o mostrar texto genérico sin código).

#### [MOV3-04] La tarjeta de conflicto no enseña qué cambia; "Reaplicar" falla en silencio
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** MovementEditorSheet.kt:394-402; MovementEditorViewModel.kt:92-93 y 279-280 (se calculan y no se usan); 449-463 (`onRedoConflict`); 440-447 (`onDiscardConflict` ignora el resultado)
- **Qué está mal:** el VM decodifica `conflictProposedOccurredAt` y `conflictProposedSourceAccountId`, pero la tarjeta solo pinta el monto. Si la propuesta cambia fecha o cuenta, ambos montos son iguales. En `onRedoConflict` cualquier resultado distinto de `Success` (Conflict, Rejected) solo recarga: no hay mensaje.
- **Escenario de fallo:** la propuesta en conflicto solo cambia la cuenta de origen. La tarjeta muestra "Versión oficial actual S/ 50.00" y "Propuesta en conflicto S/ 50.00". El usuario no puede decidir entre Descartar y Reaplicar. Si pulsa Reaplicar y el dominio la rechaza, el spinner termina y no ocurre nada visible.
- **Fix mínimo:** mostrar fecha y cuenta de la propuesta junto al monto cuando difieran (los datos ya están en el estado), y poner `generalError` en la rama `else` de `onRedoConflict`.

#### [MOV3-05] Reintentar un guardado fallido crea una clave de idempotencia nueva, aunque el mensaje promete lo contrario
- **Severidad:** media · **Categoría:** datos · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** QuickMovementViewModel.kt:1018 y 1196-1205
- **Qué está mal:** `saveCurrentMovement` genera `UUID.randomUUID()` en cada pulsación (solo el flujo de comprobante usa una clave estable). `executeRegister` captura cualquier excepción y muestra "No se pudo guardar. Puedes reintentar sin duplicar la operación." `OfflineFirstMovementRepository.registerTransaction` (líneas 110-120) hace el commit en Room y después llama a `syncScheduler.scheduleSync` y a `expenseChangeListener.onExpenseConsumptionChanged`. Si cualquiera de las dos lanza tras el commit, el movimiento ya existe pero la UI dice que falló.
- **Escenario de fallo:** gasto S/ 80 guardado en Room; el listener de gasto lanza (por ejemplo error al evaluar alertas de presupuesto). La UI muestra el error. El usuario pulsa "Reintentar guardar": clave nueva, `occurredAt` refrescado a "ahora". Si pasan menos de 5 min aparece el aviso de duplicado (el usuario puede confirmar y duplicar); si pasan más, se guarda un segundo gasto sin aviso y el saldo se descuenta dos veces.
- **Fix mínimo:** conservar la clave en el VM (`pendingKey`) desde el primer intento y limpiarla en éxito o cuando cambien monto/cuenta/categoría; así el mensaje deja de ser falso. Alternativa: que el repositorio no propague excepciones de los hooks posteriores al commit.
- **Cross-módulo:** sí → MOV2 (el repositorio/hook que lanza tras el commit); la clave por intento está en MOV3.

#### [MOV3-06] "Aplicar a deuda/meta" (saveApplication) mueve dinero y no tiene ningún test
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** QuickMovementViewModel.kt:1038-1126; QuickMovementViewModelTest.kt (no hay llamadas con `onApplicationSelected`)
- **Qué está mal:** 90 líneas de validación y envío de `settleDebt` y `reserveGoalFunds` (moneda, tipo PAYABLE/RECEIVABLE, principal contra saldo pendiente, meta bloqueada por plan, cuenta destino distinta). El constructor del test ni siquiera pasa `debtRepository` ni `goalRepository`, así que estas ramas nunca se ejecutan.
- **Escenario de fallo:** un cambio en la comparación `amountMinor > debt.remainingPrincipalMinor` o en `debt.currencyCode != currency.name` permitiría pagar de más o en otra moneda sin que ningún test falle.
- **Fix mínimo:** un test con `DebtRepository` falso: deuda PAYABLE de S/ 100, cuenta PEN, monto 150 → no se llama a `settleDebt` y `generalError` = "El principal supera el saldo pendiente.". Y un caso feliz de 60 que verifique `principalMinor=6000`.

#### [MOV3-07] El pago de tarjeta desde Quick no tiene test de fallo y depende de textos en inglés de una excepción
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** QuickMovementViewModel.kt:979-1010 (mapeo 1002-1006); QuickMovementViewModelTest.kt:1060 (`payCreditCard` falso siempre devuelve éxito)
- **Qué está mal:** el mensaje al usuario se decide comparando `error.message` con `"Insufficient funds in source account"` y `"Payment amount exceeds current card debt"`, copiados de `require(...)` en OfflineFirstFinancialInstrumentsRepository.kt:1087 y 1094. Si alguien cambia esos textos, el usuario pasa a ver el mensaje genérico sin que falle nada. Tampoco hay test de la regla "origen debe ser BANK o SAVINGS" (981-985).
- **Escenario de fallo:** se traduce o edita el `require` del repositorio; el pago con saldo insuficiente muestra "No se pudo guardar el pago de tarjeta. Revisa la cuenta..." en vez de "Saldo insuficiente".
- **Fix mínimo:** test con `payCreditCard` que devuelva `Result.failure(IllegalArgumentException("Insufficient funds in source account"))` y aserte el texto; y otro con origen de efectivo/billetera que aserte `accountError`. (Mejor a medio plazo: excepciones tipadas en ACC1/ACC5.)
- **Cross-módulo:** sí → ACC5 (los textos de `require` viven en OfflineFirstFinancialInstrumentsRepository)

#### [MOV3-08] El selector de comercio de Quick conserva el comercio del movimiento anterior
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** QuickMovementScreen.kt:158, 183-201, 302-321
- **Qué está mal:** `MerchantPickerViewModel` guarda `selectedMerchant`/`provisionalText` y la hoja los pinta arriba como "Comercio del catálogo" (MerchantPicker.kt:674-711). Tras un guardado, `QuickMovementViewModel.resetForm()` limpia el formulario pero nadie llama a `merchantPickerViewModel.clearSelection()`. `QuickMovementBottomSheet` usa `hiltViewModel()` en la entrada de navegación del historial, así que el VM del selector sobrevive. Tampoco se inicializa desde `prepareReceiptDraft` (el comercio del comprobante no aparece).
- **Escenario de fallo:** el usuario guarda un gasto en "Bembos". Abre otro gasto, entra a "Más detalles" > Comercio: el selector muestra "Bembos" como elegido aunque el formulario está vacío. Si lo toca de nuevo y el selector lo trata como ya seleccionado, la acción puede no disparar `onSelectMerchant`.
- **Fix mínimo:** en el `LaunchedEffect` de eventos, llamar `merchantPickerViewModel.clearSelection()` junto con el cierre (o llamar `initialize(...)` con el estado del VM al abrir el selector).

#### [MOV3-09] El editor no tiene tests de `onSave` (validaciones, Conflict, especializados)
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** MovementEditorViewModel.kt:347-438; MovementEditorRememberTest.kt y MovementEditorUiStateTest.kt
- **Qué está mal:** los tests existentes cubren recordar categoría y `hasUnsavedChanges`. No hay test de: monto 0 o inválido, transferencia con destino igual al origen, gasto sin categoría, la rama `Conflict`/`Rejected`, ni de `isSpecialized` (cuotas, tarjeta, kind distinto). Estas ramas deciden qué dinero se corrige.
- **Escenario de fallo:** quitar el `|| currentState.selectedDestinationAccountId == currentState.selectedSourceAccountId` (línea 367) dejaría pasar una transferencia a la misma cuenta hasta el Planner, y ningún test fallaría.
- **Fix mínimo:** tres casos en `MovementEditorRememberTest`: (a) `onAmountChanged("0")` + `onSave` → `amountError` y sin llamada a `revise`; (b) head con `cardId` → `isSpecialized` y `onSave` no llama a `revise`; (c) `revise` devuelve `Conflict` → verificar que el mensaje sobrevive (cubre también MOV3-02).

#### [MOV4-02] Una actualización descarta las páginas que el usuario ya cargó
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryViewModel.kt:940
- **Qué está mal:** Una invalidación conserva temporalmente la lista, pero al terminar la lectura la reemplaza por solo la primera página y su cursor.
- **Escenario de fallo:** Al consultar movimientos antiguos tras cargar varias páginas, llega una sincronización o modificación local: desaparecen las páginas posteriores y el usuario debe volver a cargarlas.
- **Fix mínimo:** En una actualización del mismo filtro, recargar y conservar el rango de páginas que estaba visible, manejando cambios en los cursores.

#### [MOV4-03] El número previo de resultados no representa la consulta aplicada
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt:113 y app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt:158
- **Qué está mal:** El contador filtra únicamente las filas ya cargadas de la consulta anterior; además usa medianoche UTC y no comprueba la moneda, mientras que el filtro aplicado usa días locales y moneda.
- **Escenario de fallo:** Si la primera página no contiene gastos y hay gastos en las siguientes, elegir gastos muestra «Ver 0 movimientos» aunque al aplicar sí aparecen resultados; un movimiento a las 20:00 en Lima también puede contarse en otro día.
- **Fix mínimo:** Calcular el conteo mediante la misma consulta y límites que se aplicarían al borrador, o eliminar el número hasta disponer de un conteo fiable.

#### [MOV4-06] Un rango en dólares se muestra con símbolo de soles
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt:389 y app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt:1498
- **Qué está mal:** Las dos etiquetas del borrador imprimen S/ sin tener en cuenta draft.currency.
- **Escenario de fallo:** Al seleccionar USD y un mínimo de 100, la hoja muestra «S/ 100» aunque la consulta filtra dólares.
- **Fix mínimo:** Usar la moneda seleccionada en ambas etiquetas o quitar el símbolo si todavía no se eligió moneda.

#### [MOV4-07] La paginación puede detenerse con una página casi toda oculta
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreen.kt:198
- **Qué está mal:** La observación emite solo cuando cambia el booleano «cerca del final»; el segundo disparador carga páginas solo si no queda ninguna fila visible.
- **Escenario de fallo:** Si una primera página tiene una sola fila visible porque las demás fueron ocultadas, la lista puede permanecer cerca del final desde el estado inicial de carga. La observación no emite de nuevo al recibir la página y la condición de lista vacía es falsa, de modo que las páginas restantes no se piden aunque existan.
- **Fix mínimo:** Volver a evaluar la carga al cambiar cursor o estado de carga mientras la vista siga cerca del final, evitando solicitar otra página cuando haya error o una carga en curso.

#### [MOV4-08] El aviso y el reintento al reconectar ignoran pendientes fuera de la página
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreen.kt:232
- **Qué está mal:** El número de pendientes se calcula sobre los movimientos visibles de la consulta paginada, y ese mismo número decide mostrar el aviso y programar sincronización al volver la red.
- **Escenario de fallo:** Si hay un movimiento pendiente en una página anterior o excluido por el filtro, el número queda en cero; no aparece el aviso de reconexión ni se solicita el envío inmediato al volver la red.
- **Fix mínimo:** Obtener el estado de pendientes del propietario desde la fuente completa de sincronización y usarlo para el aviso y el disparador.

#### [MOV5-02] Test instrumentado roto: aserción contra texto desactualizado ("Créd. disp.:" vs "Crédito disponible:")
- **Severidad:** media (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheetTest.kt:62
- **Qué está mal:** El commit `66a8193ac` modificó el texto visible de la fila de tarjeta de crédito en `AccountSelectorBottomSheet.kt:770` de `"Créd. disp.: "` a `"Crédito disponible: "`. No obstante, el test instrumentado `AccountSelectorBottomSheetTest.kt:62` no fue actualizado y continúa buscando el nodo con substring `"Créd. disp.:"`, fallando inmediatamente con `AssertionError`.
- **Escenario de fallo:** Al ejecutar la suite de pruebas instrumentadas en `AccountSelectorBottomSheetTest`, el método `creditCardRowShowsAvailableCreditCopyAndAccessibleMeaning` falla con `AssertionError: Failed to find node with text containing 'Créd. disp.:'`.
- **Fix mínimo:** Actualizar la aserción del test a `compose.onNodeWithText("Crédito disponible:", substring = true).assertIsDisplayed()`, o revertir el texto a `"Créd. disp.: "` si se prioriza evitar desbordamiento en pantallas pequeñas de 360dp según la especificación de Issue 48.
- **Verificación (Fable):** El test busca «Créd. disp.:» y la pantalla muestra «Crédito disponible: ». Test roto, no bug de producto.

#### [MOV5-03] En tema oscuro, los chips pastel guardan el fondo oscuro en BD en vez del código pastel canónico y no marcan selección
- **Severidad:** media (el worker dijo alta) · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:215-250 y 2346-2364
- **Qué está mal:** En `CustomPastelColorDialog`, `recommendedPastelChips` provee tokens adaptados por `themed(base)` en tema oscuro, donde `containerColor` es un fondo oscuro (ej. `Color(0xFF14532D)`), pero `hexCode` se conserva como el pastel claro (`"#DCFCE7"`). Al pulsar un chip, el diálogo extrae el HSV de `token.containerColor` (`#14532D`). Como `isSelected = currentHex.equals(token.hexCode)` compara `#14532D` con `#DCFCE7`, `isSelected` resulta siempre `false` en tema oscuro (el check no aparece nunca). Además, al guardar, se persiste `#14532D` en base de datos; cuando el usuario cambia al tema claro, la categoría queda con fondo oscuro e ilegible.
- **Escenario de fallo:** Un usuario con tema oscuro crea una subcategoría y selecciona el chip "Mint". El check no se dibuja. Al confirmar y guardar, el color persistido en Room y Supabase es `#14532D` en vez de `#DCFCE7`. Cuando el usuario usa la aplicación en tema claro, su categoría muestra un color verde oscuro opaco en lugar del tono pastel menta previsto.
- **Fix mínimo:** En `CustomPastelColorDialog.kt:2358`, calcular el HSV a partir del valor hexadecimal canónico: `val hsv = PastelColorUtils.colorToHsv(PastelColorUtils.hexToColor(token.hexCode))`.
- **Verificación (Fable):** El chip toma el HSV de `containerColor` ya tematizado: en oscuro guarda el color oscuro. Defecto visual, no de dinero.

#### [MOV5-04] En `directSelectionMode`, tocar una fila raíz con subcategorías confirma inmediatamente la raíz y cierra la hoja
- **Severidad:** media (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:955-962
- **Qué está mal:** En `BrowseCategoriesView`, el click en la fila raíz ejecuta: ```kotlin onSelectCategory(root) if (directSelectionMode) { onConfirmSelection(root) } else if (children.isNotEmpty() && !multiSelect) { onToggleRootExpand(root.id) } ``` Al estar en `directSelectionMode = true` (modo activo por defecto en `QuickMovementScreen`, `MovementEditorSheet` e `InstallmentSimulatorScreen`), tocar el cuerpo de una fila raíz llama de inmediato a `onConfirmSelection(root)`, seleccionando la categoría padre y cerrando la hoja. La rama `else if` que expandiría el acordeón nunca se alcanza. La única manera de expandir las subcategorías es pulsar con precisión el pequeño botón chevron en el borde derecho.
- **Escenario de fallo:** Un usuario en registro rápido desea asignar la subcategoría "Supermercados" dentro de "Alimentación". Toca la fila "Alimentación" esperando ver sus opciones: el selector se cierra de inmediato y asigna la categoría raíz "Alimentación", sin permitirle ver ni seleccionar la subcategoría.
- **Fix mínimo:** En el click de la fila raíz, si `children.isNotEmpty()`, ejecutar `onToggleRootExpand(root.id)` para expandir el acordeón en lugar de confirmar de inmediato, reservando la confirmación directa para hojas sin hijos o añadiendo una acción explícita.
- **Verificación (Fable):** Confirmado el código; falta decisión de producto sobre si el modo directo debe abrir subcategorías.

#### [MOV5-05] Etiqueta "Actual" en categorías es semánticamente errónea: se asigna a cualquier ítem recién pulsado
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:552, 926, 1072 y 1297
- **Qué está mal:** `BrowseCategoriesView` recibe `currentIds = effectiveSelectedIds`, el cual muta con cada clic del usuario. Además, en las líneas 926 y 1072 se evalúa `root.id in currentIds || (!multiSelect && isRootSelected)`. Esto hace que "Actual" no indique la categoría previa del movimiento, sino que se convierta en un duplicado exacto del estado seleccionado, saltando en tiempo real al ítem recién pulsado junto con el icono de check.
- **Escenario de fallo:** Al editar un movimiento que tenía categoría "Restaurantes", el usuario abre el selector y toca "Transporte". De inmediato, "Restaurantes" pierde la etiqueta "Actual" y "Transporte" pasa a mostrar "Actual", sugiriendo falsamente que "Transporte" ya estaba guardada.
- **Fix mínimo:** Guardar `val initialCategory = remember(selectedCategoryId) { selectedCategoryId }` y pasar `setOfNotNull(initialCategory)` como `currentIds`, eliminando la condición redundante `|| (!multiSelect && isRootSelected)`.

#### [MOV5-06] Pantalla de creación de categoría dentro del selector modal carece de botón "Atrás"
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:602-646
- **Qué está mal:** Al pasar a `PastelFlowScreen.CREATE_SUBCATEGORY`, `CategoryFormContent` se invoca sin `showBack = true`. Como `showBack` es `false` por defecto en `CategoryFormContent`, no se muestra la flecha de volver. El único botón en la cabecera es la "X" (`onDismiss`), que cierra toda la hoja en lugar de regresar al explorador de categorías.
- **Escenario de fallo:** El usuario pulsa "+ Nueva categoría", cambia de opinión y quiere volver a la lista de categorías. Al no haber botón de retorno, presionar la "X" descarta el picker por completo obligándolo a reabrirlo desde la pantalla de movimiento.
- **Fix mínimo:** Pasar `showBack = true` y `selectOnSave = true` en la invocación de `CategoryFormContent`.

#### [MOV5-07] Acordeón de la subcategoría seleccionada no se expande al entrar si los datos llegan asíncronamente
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:496-501
- **Qué está mal:** `var expandedRootIds by remember { mutableStateOf(...) }` no define claves de recuerdo (`remember(categories, selectedCategoryId)`). Si en el primer frame de renderizado `categories` llega vacía mientras el flujo de Room/ViewModel termina de cargar, `currentParents` se evalúa como vacío y nunca se vuelve a calcular cuando la lista de categorías se puebla.
- **Escenario de fallo:** Al abrir la edición de un movimiento con una subcategoría seleccionada, la consulta a base de datos tarda unos milisegundos en emitir: la hoja se dibuja con todos los acordeones cerrados y la subcategoría actual queda escondida dentro del acordeón colapsado.
- **Fix mínimo:** Añadir claves a `remember`: `remember(categories, selectedCategoryId, selectedCategoryIds)`.

#### [MOV5-08] Error de truncamiento y degradación cromática en ciclo HSV <-> RGB y Color a HEX
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:416-418, 1949-1951, 2268, 2291 y 2314
- **Qué está mal:** `PastelColorUtils.colorToHex` y los campos numéricos R, G, B convierten los canales float de Compose mediante `(x * 255).toInt()`. Al convertir float IEEE 754 (donde `128f / 255f * 255f = 127.99999f`), `.toInt()` trunca hacia abajo a 127 en vez de redondear a 128 (generando `#7F` en vez de `#80`). Modificar un canal en el modal altera los otros canales hacia abajo sucesivamente.
- **Escenario de fallo:** En el modal de color personalizado, el usuario tiene un color con G=128 y B=64. Al cambiar el valor de R, los campos de G y B parpadean y disminuyen a 127 y 63 sin que el usuario los haya tocado.
- **Fix mínimo:** Reemplazar `(x * 255).toInt()` por `(x * 255f).roundToInt().coerceIn(0, 255)`.

#### [MOV5-09] Resumen de `IconCatalogDialog` muestra "TV Streaming" erróneamente cuando el icono inicial no está en el catálogo hardcodeado
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:1684-1687
- **Qué está mal:** `selectedCatalogItem = remember(tempSelectedId) { CATALOG_ICONS.find { ... } ?: CATALOG_ICONS.first() }`. Si el icono inicial recibido no está entre los 48 de `CATALOG_ICONS` (por ejemplo `"pie_chart"` en presupuestos o `"category"` genérico), el elvis selecciona `"tv"`. El banner inferior afirma que el icono elegido es "TV Streaming", contradiciendo el estado real.
- **Escenario de fallo:** En `BudgetFormScreen`, al abrir el catálogo de iconos (iniciando en `"pie_chart"`), el diálogo indica abajo "Icono seleccionado: TV Streaming / Categoría: Películas & Series", a pesar de que "TV Streaming" no fue elegido.
- **Fix mínimo:** Proveer un `CatalogIconItem` dinámico de respaldo si no está en la lista o mostrar `"Icono actual: $tempSelectedId"` con `resolveCatalogIcon(tempSelectedId)`.

#### [MOV5-10] En `AccountSelectorBottomSheet`, las tarjetas de débito ignoran `cardsAllowed = false` en `ALL` y en el conteo total
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheet.kt:236, 244 y 251
- **Qué está mal:** Cuando `cardsAllowed` es `false` (propósitos `INCOME_DESTINATION` o `TRANSFER_SOURCE`), `cards` se vacía, pero `debitCards` no se condiciona por `cardsAllowed`. `cardsCount` suma `debitCards.size`, y `displayedItems` en `InstrumentFilter.ALL` incluye `debitCards`, mientras que la píldora "Tarjetas" se oculta.
- **Escenario de fallo:** Si se pasa `debitCards` con `purpose = INCOME_DESTINATION`, las tarjetas de débito aparecen listadas en la pestaña "Todas", pero la píldora "Tarjetas" no existe y el conteo de la pestaña "Todas" supera la suma de las pestañas hijas.
- **Fix mínimo:** Filtrar `debitCards` con `if (cardsAllowed) debitCards else emptyList()` tanto en `cardsCount` como en `displayedItems`.

#### [MOV5-11] Reasignación ineficiente O(N*M) de `categories.filter` en cada composición de fila de la LazyColumn
- **Severidad:** media · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:923
- **Qué está mal:** Dentro de `items(roots)` en la `LazyColumn`, cada composición de categoría raíz ejecuta `val children = categories.filter { it.parentCategoryId == root.id }`. En una lista con decenas de categorías, el scroll genera filtrados lineales completos repetidos en cada frame.
- **Escenario de fallo:** Lag y jank de scroll observable al desplazarse rápidamente por la lista de categorías en dispositivos de gama baja con muchas categorías creadas.
- **Fix mínimo:** Pre-agrupar fuera de la LazyColumn con `val childrenByParent = remember(categories) { categories.filter { it.parentCategoryId != null }.groupBy { it.parentCategoryId } }` y acceder con `childrenByParent[root.id].orEmpty()`.

#### [MOV5-12] Acoplamiento arquitectónico invertido: modales de categorías residen en `movements` y generan dependencia circular con `categories`
- **Severidad:** media · **Categoría:** dividir · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:1479-2418
- **Qué está mal:** `PastelCategoryPickerFlow.kt` (en `movements`) contiene modales completos de configuración de categorías (`ParentCategoryChooserDialog`, `IconCatalogDialog`, `CustomPastelColorDialog`) y un catálogo propio de iconos (`CATALOG_ICONS`). Son importados por `feature/categories` y `feature/budgets`. Al mismo tiempo, `PastelCategoryPickerFlow.kt` importa `CategoryFormContent` desde `feature/categories`, creando un acoplamiento circular entre módulos de presentación.
- **Escenario de fallo:** Cambios en el diseño de formularios de categorías requieren tocar `movements`, y cambios en selectores de movimientos pueden romper presupuestos y categorías.
- **Fix mínimo:** Mover `ParentCategoryChooserDialog`, `IconCatalogDialog`, `CustomPastelColorDialog`, `CATALOG_ICONS` y `PastelColorUtils` a `feature/categories/presentation/` o a `ui/component/`, dejando en `movements` solo el selector.
- **Cross-módulo:** sí → CAT2 (categories/presentation) y BUD1 (budgets)
- **Ahorro estimado:** 940 líneas en `movements`

#### [MOV5-13] Tests unitarios incompletos: propósito `CARD_PAYMENT` y títulos de `FILTER` sin cobertura en `AccountSelectorRulesTest`
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/feature/movements/presentation/AccountSelectorRulesTest.kt:39-60
- **Qué está mal:** `AccountSelectorRulesTest` no contiene ninguna prueba para `AccountSelectorPurpose.CARD_PAYMENT` (que debe prohibir tarjetas de crédito y mostrar el copy específico de pago de tarjeta) ni para los títulos de `FILTER`.
- **Escenario de fallo:** Una modificación accidental en `AccountSelectorDefaults.allowsCreditCards` que permita tarjetas de crédito en `CARD_PAYMENT` (permitiendo pagar una tarjeta con otra tarjeta de crédito) no es detectada por ningún test.
- **Fix mínimo:** Agregar test unitario en `AccountSelectorRulesTest` comprobando `assertFalse(AccountSelectorDefaults.allowsCreditCards(AccountSelectorPurpose.CARD_PAYMENT, callerAllows = true))` y `assertEquals("Elige la cuenta de origen del pago de tarjeta", AccountSelectorDefaults.subtitle(AccountSelectorPurpose.CARD_PAYMENT))`.

#### [MOV1-10] La prueba de límites ignora dependencias con nombre completo
- **Severidad:** baja · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/feature/movements/MovementsFeatureBoundaryTest.kt:33,44-48; app/src/main/java/com/kipu/app/feature/movements/domain/MovementRepository.kt:47
- **Qué está mal:** La prueba solo mira líneas import, aunque el dominio ya referencia tipos data.local con su nombre completo. Además, si no encuentra las carpetas, aprueba mediante assertTrue(true).
- **Escenario de fallo:** Añadir com.kipu.app.feature.movements.data.local.MovementConflictProposalEntity a una firma de dominio sin import → la dependencia prohibida queda presente y el test pasa. Ejecutar desde una carpeta donde no resuelve ninguna ruta también produce un verde sin inspección.
- **Fix mínimo:** Fallar si no se encuentra la carpeta y detectar las referencias calificadas al paquete prohibido, además de imports. El test mínimo de la comprobación debe incluir una firma con nombre completo y exigir una violación.

#### [MOV2-09] La rama de `dependsOnCommandId` es código muerto y además busca con la clave equivocada
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:363-381
- **Qué está mal:** ningún código de producción rellena `dependsOnCommandId`; solo lo hacen los tests y el valor por defecto `null` de `MovementRevisionCommand`. Además, la rama busca el padre con `getOutboxById(dependsOnCommandId)`, pero el valor es una clave de idempotencia (LedgerIntegrityWorkerTest.kt:201) y el id de la outbox es otro UUID.
- **Escenario de fallo:** si alguien la activa, un padre en `CONFLICT` nunca se detecta y el hijo queda en `WAITING_DEPENDENCY` cada 2 s, sumando `attempt_count`, sin final.
- **Fix mínimo:** borrar la rama y usar el orden por agregado de MOV2-02. Si se quiere conservar, buscar el padre por `idempotency_key`.
- **Ahorro estimado:** 19 líneas

#### [MOV2-11] El mapeo de `TransactionItem` está copiado dos veces en el repositorio
- **Severidad:** baja · **Categoría:** fusionar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/OfflineFirstMovementRepository.kt:53-99 y :289-327
- **Qué está mal:** `observeItems` y `mapEntitiesToItems` construyen el mismo `TransactionItem` con el mismo código, logos incluidos. Cualquier campo nuevo hay que añadirlo en los dos sitios. El `run { }` de la línea 66 no aporta nada.
- **Escenario de fallo:** alguien añade un campo (por ejemplo, el color de la cuenta) solo en `observeItems`. El historial paginado, que usa `mapEntitiesToItems`, lo muestra vacío y el inicio no.
- **Fix mínimo:** una sola función privada `toItem(entity, categories, merchants, accounts, cards)` usada por las dos rutas.
- **Ahorro estimado:** ~35 líneas

#### [MOV2-12] El recálculo de saldo está repetido cuatro veces y ya existe un helper
- **Severidad:** baja · **Categoría:** fusionar · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt:171-178, :240-245, :450-458, :480-487
- **Qué está mal:** cada sitio repite `calculateLedgerSumForAccount` más `upsertBalanceProjection` a mano. `BalanceProjectionStore.rebuildBalanceFromLedger(userId, accountId, currency)` ya lo hace y lo usa el data source local.
- **Escenario de fallo:** los dos caminos ya divergen. En 452, una cuenta que falta se salta (`return@forEach`); en 481, la misma situación aborta la transacción (`return@ownerTransaction false`).
- **Fix mínimo:** inyectar o crear `BalanceProjectionStore(movementDao)` y llamar a `rebuildBalanceFromLedger` en los cuatro sitios.
- **Ahorro estimado:** ~20 líneas

#### [MOV2-13] Rama de anulación remota con `ledger_entries` vacío probablemente muerta
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** baja · **Estado:** probable
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt:499-519 (y su test en SyncMovementsWorkerTest.kt:481-523)
- **Qué está mal:** desde 20261108173837, todo TRANSACTION UPSERT se proyecta al estado actual con todas sus filas de ledger, y una anulación trae 4 filas que suman 0 (standard_transaction_pull_projection_test.sql:101-104). Con esa migración, ya no llega «VOIDED + `ledger_entries` vacío».
- **Escenario de fallo:** ninguno en producción si la migración está desplegada; la rama solo existe para el formato viejo.
- **Fix mínimo:** confirmar el despliegue de 20261108173837 y borrar la rama con su test.
- **Ahorro estimado:** ~21 líneas (+43 del test)
- **Verificación (Fable):** La migración 20261108173837 está desplegada (comprobado el 2026-11-11), así que el formato «VOIDED con ledger vacío» ya no se emite. Antes de borrar la rama, confirmar que el pull aplica la proyección al leer y no a filas antiguas del feed.

#### [MOV2-14] Imports sin uso y helpers de propietario duplicados en el worker
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:17-34 y :107-118; MovementHydration.kt:44-52
- **Qué está mal:** unos 14 imports quedaron sin uso tras separar `MovementHydration` (`AccountEntity`, `CardEntity`, `CreditPaymentAllocationEntity`, `CreditLiabilityAccountIds`, `Card`, `FinancialApiResponse`, `PullChangesRequestDto`, `SyncFeedCursor`, `parseFeedInstant`, `MovementSyncCheckpointEntity`, `TransactionEntity`, `LedgerEntryEntity`, `decodeFromJsonElement`). `requireOwner`, `ownerRequest` y `ownerTransaction` están copiados en las dos clases.
- **Escenario de fallo:** un cambio en la guarda de propietario (por ejemplo, comprobar también la sesión remota) se aplica en una sola copia y la otra escribe datos de un usuario que ya no está activo.
- **Fix mínimo:** borrar los imports y mover los dos helpers como funciones `internal` junto a `requireSyncOwner` (línea 514).
- **Ahorro estimado:** ~25 líneas

#### [MOV2-15] Pasos de construcción en el módulo de Hilt que sobran
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/di/MovementsModule.kt:65-87
- **Qué está mal:** cuatro `@Provides` solo llaman a constructores sin argumentos. Sobra con poner `@Inject constructor()` en esas cuatro clases del dominio.
- **Escenario de fallo:** ninguno funcional; son 20 líneas de boilerplate.
- **Fix mínimo:** añadir `@Inject constructor()` a `RegisterTransactionValidator`, `TransactionRequestHasher`, `MovementRevisionRequestHasher` y `MovementRevisionPlanner`, y borrar el `object MovementsModule`. Los tests que los crean a mano siguen funcionando.
- **Cross-módulo:** sí → MOV1 (las anotaciones van en domain) No dejé fuera hallazgos menores.
- **Ahorro estimado:** ~22 líneas

#### [MOV3-10] La carga inicial del editor puede dejar `initialCategoryName` en null
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** MovementEditorViewModel.kt:191-197 y 248-262
- **Qué está mal:** si `loadTransaction` termina antes de que lleguen las categorías, `initialCategoryName = matchingCat?.name` queda null. El colector de categorías actualiza `selectedCategoryName` pero nunca `initialCategoryName`.
- **Escenario de fallo:** catálogo grande o dispositivo lento; el usuario cambia la categoría y el resumen "Qué pasará al guardar" dice "Categoría — Antes: Ninguno" y la fila "Antes: Sin categoría", aunque el movimiento tenía categoría.
- **Fix mínimo:** en el colector (línea 193-196) añadir `initialCategoryName = options.firstOrNull { it.id == current.initialCategoryId }?.name ?: current.initialCategoryName`.

#### [MOV3-11] Quick transforma "," en "." y rechaza montos con separador de miles
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** QuickMovementViewModel.kt:499-503; core/finance/domain/MoneyInputParser.kt (cross-módulo, solo lectura)
- **Qué está mal:** `onAmountChanged` hace `replace(',', '.')`. `MoneyInputParser` sabe distinguir "1,234.50", pero recibe "1.234.50" y devuelve null. La capacidad del parser queda inalcanzable desde Quick.
- **Escenario de fallo:** el usuario pega o escribe "1,500.50" → `amountError` "Ingresa un monto válido mayor a 0" sin explicación. El editor no tiene este problema (usa el texto sin reemplazar).
- **Fix mínimo:** quitar el `.replace(',', '.')` y dejar que el parser decida (el teclado decimal ya limita a dígitos y separador).

#### [MOV3-12] El impacto de presupuesto hace una consulta por presupuesto en cada tecla y parpadea
- **Severidad:** baja · **Categoría:** rendimiento · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** QuickMovementViewModel.kt:325-355 (línea 333)
- **Qué está mal:** el flujo `combine` se reinicia con cada cambio de `amountText`. Cada vez se vacía `budgetImpacts` y se ejecuta `getBudgetConsumption.current` por cada presupuesto activo. `collectLatest` cancela el trabajo anterior, pero el vaciado inmediato hace que la tarjeta de presupuesto desaparezca y reaparezca en cada dígito (y mueve el layout).
- **Escenario de fallo:** usuario con 8 presupuestos activos escribe "125.50": 6 consultas a Room por tecla en main-safe pero con parpadeo de la tarjeta "Impacto en presupuesto" en cada dígito.
- **Fix mínimo:** `debounce(250)` sobre el `_uiState.map{...}` y no vaciar `budgetImpacts` hasta tener el nuevo resultado (o hasta que la condición deje de cumplirse).

#### [MOV3-13] El reembolso permite cualquier fecha (antes de la compra o futura)
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** baja · **Estado:** probable (confianza baja del worker, no reabierto)
- **Ubicación:** MovementRefundSheet.kt:204-218; dominio: domain/RegisterRefundTransaction.kt:67-86 (sin validación de `occurredAt`)
- **Qué está mal:** el `DatePickerDialog` no restringe fechas, y el dominio no compara `occurredAt` con la fecha de la compra ni con "hoy". El texto de la hoja dice "Fecha real del reembolso".
- **Escenario de fallo:** compra del 15-sep, el usuario toca por error el 15-ago o 2027: el ingreso por reembolso queda antes de la compra o en el futuro, y puede cambiar saldos por fecha y los reportes.
- **Fix mínimo:** `DatePicker(selectableDates = ...)` con rango [fecha de compra, hoy] y la misma validación en el caso de uso.
- **Cross-módulo:** sí → MOV1 (la validación de dominio de `RegisterRefundTransaction`)

#### [MOV3-14] Dos definiciones distintas de "movimiento especializado"
- **Severidad:** baja · **Categoría:** fusionar · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** MovementEditorViewModel.kt:220-226 vs VoidMovementDialog.kt:57-65
- **Qué está mal:** el VM considera especializado cualquier `operationKind` no vacío (incluido "STANDARD") y no incluye `legacyKind = DEBT_PAYMENT`; `isSpecializedMovement` (usada por el detalle) hace lo contrario en ambos puntos. Hoy `STANDARD` se guarda como null en local, así que no falla, pero es una regla duplicada que puede divergir.
- **Escenario de fallo:** un movimiento con `legacyKind=DEBT_PAYMENT` y `operationKind=null` se abre en el editor por otra ruta (por ejemplo desde ReceiptsNavigation.kt:108): el VM lo trata como editable y el Planner lo rechaza con `OPERATION_SPECIALIZED`, mostrado como código crudo.
- **Fix mínimo:** construir un `Transaction` en el VM ya cargado (`tx`) y usar `isSpecializedMovement(TransactionItem(tx))`; borrar las 6 líneas locales.
- **Ahorro estimado:** 7 líneas

#### [MOV3-15] Código muerto en el área de formularios
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** QuickMovementViewModel.kt:733-745 (`createSubcategory`, sin llamadores), 179 y QuickMovementScreen.kt:198 (`ShowMessage` nunca se emite), MovementEditorViewModel.kt:129 y MovementEditorSheet.kt:128 (idem), MovementEditorViewModel.kt:92-93, 279-280 (campos del conflicto que nadie lee, ver MOV3-04), parámetro `rememberFrequentMerchant` (QuickMovementViewModel.kt:661, ignorado), MovementDetailSheet.kt:54 (`onRemoveReceipt` jamás se usa)
- **Qué está mal:** tras grep de todo el árbol (main, test, androidTest) no hay llamadores ni emisores. Consecuencia visible: `MovementHistoryScreen` pasa `onRemoveReceipt`, pero el detalle no tiene botón para quitar el comprobante, así que un PDF adjuntado por error no se puede quitar.
- **Escenario de fallo:** el usuario adjunta el PDF equivocado y no hay forma de quitarlo; el parámetro y el código del llamador existen pero la UI no lo expone.
- **Fix mínimo:** o borrar el código muerto, o añadir el botón "Quitar" en el banner de comprobante (la función ya existe en el llamador). Para `onRemoveReceipt` decide producto (ver Dudas).
- **Ahorro estimado:** 30 líneas

#### [MOV3-16] La vista previa de anulación de un ingreso no nombra la cuenta
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** VoidMovementDialog.kt:436
- **Qué está mal:** para `INCOME` el rótulo usa `item.destinationAccountAlias ?: "Cuenta"`. El registro rápido y el editor guardan el ingreso con la cuenta en `sourceAccountId` y `destinationAccountId = null`; el propio detalle (MovementDetailSheet.kt:453) usa `sourceAccountAlias ?: destinationAccountAlias`. Se proyecta el saldo de `sourceAccountId` (línea 394-401) pero se etiqueta con otro campo.
- **Escenario de fallo:** usuario con 3 cuentas anula un ingreso: la tarjeta "Impacto en saldos" dice "Cuenta · ingreso" sin decir cuál; no puede verificar qué cuenta se descuenta.
- **Fix mínimo:** `item.sourceAccountAlias ?: item.destinationAccountAlias ?: "Cuenta"`.

#### [MOV3-17] Los textos libres (nota, comercio, motivo de anulación) no tienen longitud máxima
- **Severidad:** baja · **Categoría:** escala · **Confianza del worker:** baja · **Estado:** probable (confianza baja del worker, no reabierto)
- **Ubicación:** QuickMovementScreen.kt:1029-1044; MovementEditorSheet.kt:1003-1017; VoidMovementDialog.kt:284-298; RegisterTransactionValidator.kt (sin límite); supabase/migrations (sin CHECK de longitud en `note`)
- **Qué está mal:** ni la UI, ni el validador, ni la base remota limitan la longitud. El texto viaja en el outbox y en el payload de sync.
- **Escenario de fallo:** el usuario pega un texto de varios MB en la nota; Room lo guarda, pero la petición de sync supera el límite del cuerpo de la edge function y el movimiento queda en `FAILED_PERMANENT` (reaparece como "No se pudo sincronizar").
- **Fix mínimo:** `onValueChange = { if (it.length <= 500) ... }` en la nota y el motivo, y la misma comprobación en el validador.
- **Cross-módulo:** sí → SQL4 (si se quiere un CHECK en servidor)

#### [MOV3-18] Tests débiles o frágiles
- **Severidad:** baja · **Categoría:** test-faltante · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/androidTest/.../MovementRefundScreenTest.kt:94-123; app/src/test/.../QuickMovementViewModelTest.kt:336-339
- **Qué está mal:** `showsPurchaseAndRefundPeriodsMasksMoneyAndSubmitsMinorUnits` promete comprobar periodos, pero solo afirma `submittedDate > 0L`. No afirma que la fecha enviada sea la de hoy a las 00:00 locales ni que respete el cambio de fecha. El test `stale form currency...` fuerza el estado con reflexión sobre el campo privado `_uiState`, que se rompe con cualquier renombre.
- **Escenario de fallo:** cambiar `occurredAt` de refund por `System.currentTimeMillis() - 86_400_000` dejaría el test en verde.
- **Fix mínimo:** afirmar `submittedDate == LocalDate.now().atStartOfDay(zone)...` tras elegir fecha (o al menos que esté dentro del día actual); sustituir la reflexión por una función `internal` de prueba o por un `SavedStateHandle` precargado.

#### [MOV5-01] Crash por `ArrayIndexOutOfBoundsException` con hash codes negativos en selección de paleta pastel
- **Severidad:** baja (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:271
- **Qué está mal:** En `resolvePastelToken`, la selección de chip pastel por defecto ante nombres no reconocidos ejecuta `val index = Math.abs(name.hashCode()) % BaseRecommendedPastelChips.size`. En la JVM, `Math.abs(Int.MIN_VALUE)` desborda el rango de enteros con signo y retorna `-2147483648`. Al calcular el módulo (`% 12`), el resultado es `-8`, lo que lanza `ArrayIndexOutOfBoundsException` al indexar la lista inmutable y aborta la composición de la UI.
- **Escenario de fallo:** Si una categoría existente o sincronizada tiene un nombre cuyo hash code sea exactamente `Integer.MIN_VALUE` (o cualquier caso de desbordamiento de `Math.abs`), abrir el selector de categorías lanza de inmediato `java.lang.ArrayIndexOutOfBoundsException: Index -8 out of bounds for length 12` y cierra la aplicación.
- **Fix mínimo:** Reemplazar por `(name.hashCode() and 0x7FFFFFFF) % BaseRecommendedPastelChips.size` o `Math.floorMod(name.hashCode(), BaseRecommendedPastelChips.size)`.
- **Verificación (Fable):** `Math.abs(hashCode)` solo es negativo para `Int.MIN_VALUE`; no falla con hash negativos en general. Arreglo de una línea (`Math.floorMod`).

#### [MOV5-14] Parámetro `mostUsedAccountId` es código muerto sin efecto en `AccountSelectorBottomSheet.kt`
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheet.kt:188, 422, 430 y 637
- **Qué está mal:** El badge "Más usada" fue eliminado en refactorizaciones previas. El parámetro `mostUsedAccountId: String? = null` se mantiene, se evalúa `val isMostUsed = ...` para cada fila, pero `isMostUsed` es totalmente ignorado en `AccountItemRow` y no afecta el orden ni la presentación.
- **Escenario de fallo:** Los llamadores pasan `mostUsedAccountId` esperando un impacto visual o de ordenamiento que no existe.
- **Fix mínimo:** Eliminar el parámetro `isMostUsed` de `AccountItemRow` y retirar el cómputo redundante en `AccountSelectorBottomSheet`.
- **Ahorro estimado:** 12 líneas

#### [MOV5-15] `CreateSubcategoryView` es una función Composable muerta sin ningún llamador
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:1431-1472
- **Qué está mal:** Función interna de 42 líneas que envuelve `CategoryFormContent`, nunca invocada en ningún lugar del repositorio.
- **Escenario de fallo:** Código residual no utilizado que aumenta el tamaño del archivo y el coste de lectura.
- **Fix mínimo:** Eliminar `CreateSubcategoryView`.
- **Ahorro estimado:** 42 líneas

#### [MOV5-16] `PastelCategoryPickerFlow` es una función alias sin llamadores
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:657-687
- **Qué está mal:** Función alias que redirige a `PastelCategoryPickerBottomSheet`. Ningún archivo en todo el proyecto la utiliza (todos llaman directamente a `PastelCategoryPickerBottomSheet`).
- **Escenario de fallo:** Sobrecarga innecesaria de APIs públicas internas en el paquete.
- **Fix mínimo:** Eliminar la función alias `PastelCategoryPickerFlow`.
- **Ahorro estimado:** 31 líneas

#### [MOV5-17] Row huérfano con espaciado sin elementos hermanos en `AccountSelectionRow`
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectionRow.kt:108-124
- **Qué está mal:** `Text(text = alias)` está contenido dentro de un `Row(horizontalArrangement = Arrangement.spacedBy(6.dp))` sin ningún otro hijo y con `Modifier.weight(1f, fill = false)` redundante.
- **Escenario de fallo:** Estructura de layout innecesariamente anidada que añade sobrecarga de composición a cada fila de cuenta.
- **Fix mínimo:** Eliminar el `Row` intermedio y dejar `Text(text = alias)` directamente en el `Column`.
- **Ahorro estimado:** 15 líneas

#### [MOV5-18] Duplicación de lógica: `accountTypeLabel` y `networkLabel` duplicados en múltiples módulos
- **Severidad:** baja · **Categoría:** fusionar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheet.kt:881-895
- **Qué está mal:** `networkLabel` está duplicado exactamente entre `AccountSelectorBottomSheet.kt:881` y `UnifiedInstrumentFormScreen.kt:1521`. `accountTypeLabel` está duplicado idénticamente entre `AccountSelectorBottomSheet.kt:889` y `QuickMovementScreen.kt:1482`.
- **Escenario de fallo:** Riesgo de divergencia de textos si se agrega un nuevo tipo de cuenta o red de tarjeta y se olvida actualizar uno de los archivos.
- **Fix mínimo:** Centralizar `accountTypeLabel(AccountType)` y `networkLabel(CardNetwork)` en `AccountModels.kt` o `CalmEmeraldInstrumentComponents.kt`.
- **Cross-módulo:** sí → ACC2 (accounts/presentation/instruments) y MOV3 (movements/presentation)
- **Ahorro estimado:** 25 líneas

#### [MOV5-19] Parámetro obligatorio no nulo `onCreateCategory` fuerza a múltiples pantallas a pasar lambdas que lanzan excepciones
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:465-471
- **Qué está mal:** En `MovementFiltersSheet`, `MovementEditorSheet`, `BudgetFormScreen` y `PastelCategoryPickerSelectorTest`, donde `allowCreate = false`, los llamadores deben escribir `{ _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) }` porque el parámetro no es anulable ni tiene valor por defecto.
- **Escenario de fallo:** Código repetitivo y frágil en múltiples características.
- **Fix mínimo:** Definir `onCreateCategory: (suspend (parentId: String?, name: String, iconId: String, colorHex: String, rememberFrequentMerchant: Boolean) -> Result<CategoryOption>)? = null`.
- **Ahorro estimado:** 20 líneas en llamadores

#### [MOV5-20] Riesgo de clipping horizontal en filas de tarjetas de crédito en pantallas estrechas (360dp)
- **Severidad:** baja · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheet.kt:768-810
- **Qué está mal:** En `CreditCardItemRow`, el bloque de montos contiene dos filas con texto largo: `"Crédito disponible: "` y `"Deuda: "` seguidas de `MoneyText`. En dispositivos con ancho de 360dp, este bloque consume ~170dp, reduciendo drásticamente el espacio del título y alias de la tarjeta, que sufren truncamiento severo a pocos caracteres.
- **Escenario de fallo:** En un teléfono de 360dp, una tarjeta "BCP Visa Platinum" muestra su nombre cortado como "BCP..." debido al ancho fijo consumido por las etiquetas de crédito y deuda.
- **Fix mínimo:** Restaurar la etiqueta concisa `"Créd. disp.: "` (como exigía la prueba de copy de Issue 48) o estructurar etiqueta y monto verticalmente en anchos reducidos.

### Cuentas y tarjetas

Porciones: ACC1 (Codex gpt-6.1-sol high); ACC5 (Codex gpt-6.1-sol high); ACC2 (Claude opus medium (relevo de Codex)); ACC3 (Codex gpt-6.1-terra medium (2 pasadas)); ACC4 (Gemini 3.8 flash-high). Hallazgos: 61 (12 alta, 36 media, 13 baja).

#### [ACC1-01] Las cuotas guardan intereses pero el saldo pendiente los descarta
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/data/local/CreditEntities.kt:124-145
- **Qué está mal:** `before_refund_minor` resta pagos únicamente de `principal_minor`; nunca incorpora `interest_minor`. Esta misma consulta alimenta deuda, cuotas pendientes y estado PAID. El repositorio guarda capital e interés separados (OfflineFirstFinancialInstrumentsRepository.kt:1436-1437) y muestra el total como su suma (:1871-1874), por lo que no hay compensación en el mapeo.
- **Escenario de fallo:** Una cuota confirmada con 1.000 céntimos de capital y 100 de interés muestra total 1.100 pero pendiente 1.000. Un pago de 1.000 la marca pagada y oculta los 100 restantes; intentar pagar 1.100 se rechaza por exceder la deuda calculada. El asignador recibe precisamente este saldo en OfflineFirstFinancialInstrumentsRepository.kt:1154-1164.
- **Fix mínimo:** Para cuotas financiadas confirmadas, calcular el pendiente desde `principal_minor + interest_minor` antes de pagos y devoluciones. Añadir un caso Room con capital 1.000, interés 100 y pago 1.000: pendiente 100 y estado PARTIAL. No sumar intereses de una vista previa; la contradicción documental sobre financiación confirmada se registra en Dudas.
- **Verificación (Fable):** `outstanding = principal - paid`: el interés guardado no entra. Decidido el 2026-11-11: la deuda incluye el interés de las compras confirmadas con interés (§8.5).

#### [ACC1-02] Reactivar débito exige un cupo adicional aunque su banco ya lo ocupa
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/domain/model/Card.kt:53-54; app/src/main/java/com/kipu/app/feature/accounts/domain/usecase/AccountUseCases.kt:118-124
- **Qué está mal:** `Card.isComputableForQuota` devuelve true también para débito. El detalle transmite ese valor a `ReactivateInstrument`, que rechaza antes de llegar al repositorio cuando hay cuatro cupos. El cálculo local real agrupa débito y banco en un mismo cupo (InstrumentQuotaGroups.kt:11-12); el registro de débito ya respeta esa regla.
- **Escenario de fallo:** Usuario Free con cuatro bancos activos y una tarjeta de débito archivada vinculada a uno de ellos → pulsa Reactivar → recibe QuotaExceededException, aunque la reactivación mantiene cuatro grupos. Llamadores comprobados: presentation/detail/AccountDetailScreen.kt:327-328 y presentation/AccountsViewModel.kt:423-430; se citan solo como contrato.
- **Fix mínimo:** Hacer que la decisión de reactivar compruebe si agrega un grupo al cupo, usando el banco activo vinculado. No basta cambiar siempre débito a false: InstrumentQuotaGroupsTest comprueba que un padre archivado ya no absorbe su cupo. Añadir el caso de cuatro bancos activos y débito archivado de uno de ellos, que debe reactivarse.

#### [ACC2-01] "Editar tarjeta" permite guardar un número de tarjeta completo (PAN) como alias
- **Severidad:** alta · **Categoría:** seguridad · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CreditCardTermsSheet.kt:117-124 (campo alias sin validación); destino app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:788
- **Qué está mal:** el campo alias solo recorta a 40 caracteres. No llama a `Card.isForbiddenSensitiveCardInput`, cosa que el formulario de alta sí hacía. El repositorio escribe `aliasToSave` directo en Room y en la cola de sincronización, sin construir un `CreditCard` que lo valide.
- **Escenario de fallo:** el usuario abre "Editar tarjeta" y escribe "4557 8800 1122 3344" (o "2024") en "Nombre visible o alias" → se guarda en la base local y se encola hacia Supabase. Al leer la tarjeta otra vez, `CardEntity.toDomain()` (repo:1628+) construye `CreditCard` y su `init` lanza `require(!isForbiddenSensitiveCardInput(alias))`. La tarjeta deja de cargarse o la pantalla falla.
- **Fix mínimo:** en `CreditCardTermsSheet`, `val validAlias = alias.isBlank() || !FinancialCard.isForbiddenSensitiveCardInput(alias)`, mostrar el error y sumarlo a `canSave`. El repositorio también debería rechazarlo (ACC5).
- **Cross-módulo:** sí → ACC5 (el repositorio `updateCreditCardTerms` no valida el alias)

#### [ACC2-02] El alta crashea la app con un alias válido para la UI pero inválido para el dominio
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/UnifiedInstrumentFormScreen.kt:455-533 (`handleSaveCreditCard` no valida el alias), :985-986 (alias de tarjeta personalizada sin límite), :1177-1182 y :541-549 (alias de cuenta sin tope de 80); AccountAddFormComponents.kt:130-173 (`AddFormTextField` sin `maxLength`)
- **Qué está mal:** `handleSaveCreditCard` envía el alias sin revisar si está vacío, si parece un PAN o CVV, ni su largo. `handleSubmit` revisa vacío y PAN, pero no el máximo de 80. `AccountsViewModel.registerCreditCard` y `createAccount` construyen `CreditCard` y `Account` dentro de `viewModelScope.launch` sin try. Los `require` de dominio lanzan la excepción y nadie la captura: el repo no tiene `CoroutineExceptionHandler`.
- **Escenario de fallo:** el asistente de crédito, con "Registrar tarjeta personalizada", nombre "1234" (o 81 caracteres) y "Guardar tarjeta" → `IllegalArgumentException` en `Card.validateCardCommon` → la app se cierra. Lo mismo pasa con una cuenta, billetera o efectivo con un alias de 81 caracteres (`Account.init`, Account.kt:81).
- **Fix mínimo:** en el formulario, `onValueChange = { alias = it.take(80) }` en los dos campos de alias, y en `handleSaveCreditCard` repetir el chequeo de `handleSubmit` (vacío / `Card.isForbiddenSensitiveCardInput`) antes de llamar al VM. En ACC4, envolver la construcción en `runCatching` y emitir `AccountUiEvent.Error`.
- **Cross-módulo:** sí → ACC4 (`AccountsViewModel.registerCreditCard/createAccount` construyen el dominio sin capturar)

#### [ACC2-03] Las tarjetas CMR Falabella, Ripley y Cencosud nunca encuentran su tasa referencial
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/RateCatalogContext.kt:52-64; origen UnifiedInstrumentFormScreen.kt:516 (`issuer = bank.name`) e InstrumentFormModels.kt:112-144
- **Qué está mal:** el alta guarda `issuer = bank.name` ("Tarjeta CMR Falabella", "Tarjeta Ripley", "Tarjeta Cencosud / Metro", "Banco de la Nación"). `resolveRateCatalogContext` exige `product.institutionCode.equals(card.issuer, ignoreCase = true)`, y el código es "BANCO_FALABELLA", "RIPLEY", "CENCOSUD" o "BANCO_NACION". Solo funciona cuando nombre y código coinciden (BCP, BBVA, Interbank, Scotiabank, BanBif). El fallback `CardStylePresets.forProduct(card.issuer, ...)` falla por la misma razón.
- **Escenario de fallo:** el usuario registra "CMR Visa" eligiéndola del catálogo SBS (paso 2 filtra por `bank.code` = BANCO_FALABELLA) y abre "Tasas de Interés y TEA" → ve "No hay una referencia inequívoca para CMR Visa", aunque el producto exacto está en el catálogo.
- **Fix mínimo:** en `resolveRateCatalogContext`, normalizar el emisor antes de comparar: `val issuerCode = bankChoices.firstOrNull { it.name.equals(card.issuer, true) }?.code ?: card.issuer`, y usar `issuerCode` en el filtro y en `forProduct`. Añadir un caso a `RateCatalogContextTest` con `issuer = "Tarjeta CMR Falabella"`.

#### [ACC2-04] La ficha del tarifario muestra cifras inventadas como "datos oficiales SBS"
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/RateCatalogScreen.kt:1177, :1188-1193, :1215-1273, :1322, :1354, :1389, :1405-1432; también :898-900 y :1036-1048
- **Qué está mal:** `ReferentialRateCard` muestra lo siguiente para cualquier producto: - "Disposición de efectivo 81.50%" fijo. - TCEA "161.74%" si `publishedTceaSummary` es null. - "100% Exonerable: Consumo mínimo mensual de S/ 1,200…" si no hay condición. - "Datos oficiales verificados al 24/09/2026" si no hay fecha. - "Cálculo a 12 cuotas de S/ 1,000" y la membresía en `membershipFeePenMinor` con "S/", incluso para una tarjeta en USD. El botón "Ver tarifario oficial" no tiene `onClick` y `sourceUrl` nunca se usa. `PersonalTeaComparisonCard`, cuando el producto no publica TEA (caso cubierto por test), dibuja un rango de 0.00% a 100.00% como "Tasa mínima" y "Tasa máxima SBS".
- **Escenario de fallo:** una tarjeta BBVA en USD con un producto resuelto que no publica TCEA ni membresía en USD → la pantalla dice "TCEA 161.74%", "TEA de retiro 81.50%" y "Membresía S/ …", y además "Transparencia SBS Regulada · Datos oficiales verificados". El usuario decide con números que no son de su tarjeta.
- **Fix mínimo:** borrar la tarjeta "Disposición de efectivo" y todos los literales de fallback. Mostrar "No publicado" cuando el campo sea null, usar `membershipFeeUsdMinor` y el símbolo según `currency`, y quitar el botón (o abrir `sourceUrl`). En la comparativa, no dibujar la barra si `minBps` o `maxBps` es null. Es casi todo borrado.
- **Verificación (Fable):** Fallback literal `"161.74%"` en `RateCatalogScreen.kt:1191`.

#### [ACC3-01] Un resumen de tarjeta ausente se presenta como deuda cero
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:203-205, 1449-1452, 1890-1892
- **Qué está mal:** El detalle obtiene el resumen del dashboard, pero cuando no está disponible inventa deuda cero, crédito disponible igual a la línea y uso cero. También oculta el botón de pago al interpretar el resumen ausente como deuda cero.
- **Escenario de fallo:** Si falla la carga del dashboard mientras sí carga una tarjeta con deuda de S/ 900, el detalle muestra S/ 0 de deuda y toda la línea disponible, y no ofrece pagarla.
- **Fix mínimo:** Representar el resumen ausente como carga o error con reintento, sin cifras ni decisiones basadas en cero; agregar un test con tarjeta cargada y resumen ausente.

#### [ACC3-02] El filtro de ciclo no usa la fecha de corte
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:2105-2137, 2418-2422
- **Qué está mal:** «Ciclo actual» filtra los últimos 30 días desde el instante actual y la función ni siquiera recibe el día de corte de la tarjeta.
- **Escenario de fallo:** Con corte el día 20 y fecha actual 21, una compra del día 19 aparece en el ciclo actual aunque pertenece al anterior; un ciclo que cruce febrero tampoco dura siempre 30 días.
- **Fix mínimo:** Pasar el día de corte y filtrar por los límites reales del ciclo usando fechas locales; probar movimientos a ambos lados del corte y febrero.

#### [ACC5-02] Dos ediciones offline envían la misma revisión antigua
- **Severidad:** alta · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:458-480; app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:221-224,467-489
- **Qué está mal:** Se captura `remoteRevision` al guardar cada edición y el worker la reenvía sin actualizarla tras confirmar su predecesor. La corrección para revisión cero no cubre esta situación; solo la edición de identidad de débito tiene una actualización antes del primer envío.
- **Escenario de fallo:** Cuenta en revisión 5; offline se cambia el alias a A y después a B. Ambos comandos esperan 5; el primero deja el servidor en 6, y el segundo falla por revisión aunque sea la siguiente edición del mismo dispositivo. Sucede también con dos cambios de condiciones de crédito.
- **Fix mínimo:** Antes del primer envío de un sucesor local, persistir la revisión confirmada de su predecesor en el payload, como hace la ruta de identidad de débito; conservar ese payload para reintentos y no sobrescribir conflictos reales de otro dispositivo. Test mínimo: dos ediciones offline sobre revisión 5 terminan sincronizadas en orden.

#### [ACC5-03] Un rechazo bloquea todos los comandos posteriores del instrumento
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:1204-1212,1464-1472; app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:269-280,769-776
- **Qué está mal:** Los comandos nuevos usan siempre el último comando como predecesor, incluso si terminó en rechazo o conflicto. El contrato de `InstrumentSyncDao.getPendingCommands` y `claimCommand` solo permite continuar si ese predecesor está `SYNCED`; el worker nunca resuelve la cadena fallida.
- **Escenario de fallo:** Un pago es rechazado y compensado; el usuario registra después una compra válida con otra operación. Esta queda pendiente detrás del pago `FAILED_PERMANENT` y nunca se envía, por más reintentos que haga WorkManager.
- **Fix mínimo:** Resolver los sucesores al terminar un rechazo: rechazar explícitamente los que dependen del efecto fallido y permitir que las operaciones independientes posteriores partan del último estado confirmado. Test mínimo con DAO real: rechazo de un pago, nueva compra válida y nueva ejecución del worker.

#### [ACC5-04] Un 401 convierte cambios de tarjeta en fallos permanentes
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:453-455,497-499
- **Qué está mal:** Las rutas de TEA personal y condiciones de crédito clasifican HTTP 401 como permanente. Las rutas generales y de compras/pagos sí lo excluyen. La API devuelve 401 cuando falta temporalmente la sesión autenticada o el servidor rechaza el token.
- **Escenario de fallo:** Se cambian las condiciones offline; al reconectar, el primer envío recibe 401 con el propietario todavía activo. El comando queda `FAILED_PERMANENT`, el valor local sigue cambiado y renovar la sesión no provoca su reenvío; además bloquea sucesores por ACC5-03.
- **Fix mínimo:** Excluir 401 de la condición permanente en ambas rutas y mantener el comando reintentable. Test mínimo: 401 seguido de APPLIED conserva la operación y termina en SYNCED.
- **Verificación (Fable):** `400..499` excepto 408/429 → `FAILED_PERMANENT`, incluido 401, en las dos rutas citadas.

#### [ACC5-05] Una creación rechazada conserva una cuenta y un saldo ficticios
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:197-253; app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:269-280
- **Qué está mal:** Un rechazo definitivo de CREATE_ACCOUNT solo modifica la cola. No compensa el OPENING, no retira la cuenta local ni expone su fallo en el modelo Account. Los observadores continúan incluyendo la cuenta activa y su saldo.
- **Escenario de fallo:** Usuario Free con tres instrumentos: dos dispositivos offline crean uno cada uno con S/100 de apertura. Cada dispositivo ve cuatro y acepta el alta; el servidor acepta una y rechaza la quinta por cupo. El dispositivo rechazado conserva su cuenta inexistente y S/100 adicionales; un pull no puede borrar una entidad que nunca existió remotamente.
- **Fix mínimo:** Compensar de forma atómica una creación definitivamente rechazada y representar su estado fallido; si ya hay movimientos dependientes, conservar evidencia y resolverlos sin mostrar ese saldo como confirmado. Test mínimo: rechazo FREE_LIMIT_REACHED tras crear una cuenta con apertura no deja saldo utilizable ni una cuenta activa normal.

#### [ACC1-03] Los tests de compensación no alcanzan el rechazo simulado
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/accounts/FinancialRejectionCompensationTest.kt:64-67,191-195,371-375
- **Qué está mal:** El setup fija `currentOwner = null` mientras el input lleva un usuario concreto. El worker devuelve retry al comprobar que ese usuario no coincide con el propietario verificado (data/sync/SyncInstrumentCommandsWorker.kt:102-114), antes de procesar comandos. Además los stubs de API usan el argumento opcional por defecto, pero el worker pasa `expectedOwner = cmd.userId` (:328-329).
- **Escenario de fallo:** Ejecutar cualquiera de los dos tests con el código actual → el worker retorna retry sin invocar el rechazo, y la aserción de success falla. Aunque se corrija el propietario, los stubs no corresponden al parámetro owner del llamado real; no queda validada la compensación que anuncian los tests.
- **Fix mínimo:** Simular un propietario verificado con el mismo userId, ajustar ambos stubs al owner explícito y verificar que cada API fue invocada una vez. Conservar las aserciones de movimientos, cuotas y asientos después del rechazo: ese es el caso mínimo que falta hacer alcanzable. El worker se leyó para entender el contrato, sin atribuirle un defecto.

#### [ACC1-04] La prueba de integridad verifica un objeto que nunca se persiste
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/feature/accounts/MovementClassificationIntegrityTest.kt:75-114,118-136
- **Qué está mal:** `originalMovement` se construye como un valor local y no se entrega al repositorio ni se inserta en una base. Las aserciones monetarias consultan ese mismo objeto inmutable después del comando. La segunda prueba anuncia preservación del ledger pero solo consulta clasificación en un fake.
- **Escenario de fallo:** Una regresión modifica importe o asientos al reclasificar un movimiento → estas pruebas continúan comprobando los literales del objeto original y un mapa de clasificación, sin observar la fila o los asientos afectados. No afirman que exista tal regresión; no pueden detectarla.
- **Fix mínimo:** Sustituir el chequeo del objeto local por un caso Room: insertar movimiento y asientos, reclasificar con el repositorio real y volver a leer importe, moneda, estado, fechas y asientos. Para el borrado de comercio, reutilizar ese mismo caso y verificar que solo cambia la clasificación.

#### [ACC2-05] La pantalla de tasas propone una TEA de 42% inventada como "tu tasa"
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/RateCatalogScreen.kt:133
- **Qué está mal:** si la tarjeta no tiene `personalTeaBps`, el campo se rellena con "42.00". La comparativa dibuja "Tu tasa: 42.00%" y el botón "Actualizar mi TEA" queda activo.
- **Escenario de fallo:** una tarjeta recién creada sin TEA → el usuario entra a "Tasas", ve 42.00% y toca "Actualizar mi TEA" pensando que es su dato → se guarda 4200 bps y las simulaciones de cuotas usan una tasa que no es la de su contrato.
- **Fix mínimo:** `personalTeaInput = draft` (vacío si no hay dato). El botón ya se desactiva con el campo en blanco.

#### [ACC2-06] El alta de crédito no avisa de tarjetas duplicadas (regla de la spec)
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/UnifiedInstrumentFormScreen.kt:455-533 (y 600-634 para débito); la lógica solo existe en CardFormScreen.kt:468-477, que es código muerto
- **Qué está mal:** specs/002-ep-cta-cuentas-tarjetas/spec.md:268 exige que, si emisor, red y últimos 4 dígitos coinciden, "el segundo registro exige advertencia y alias distinguible". La pantalla en producción nunca llama a `viewModel.checkDuplicateCard`. Además, con un producto de catálogo el alias es siempre el nombre del producto (línea 510).
- **Escenario de fallo:** el usuario registra dos veces "Visa Oro LATAM Pass ••••1234" de BCP → se crean dos tarjetas idénticas sin aviso, con el mismo nombre visible, y no se distinguen en el dashboard ni en el selector de pago.
- **Fix mínimo:** en `handleSaveCreditCard`, antes de `registerCreditCard`, hacer `scope.launch { if (viewModel.checkDuplicateCard(bank.name, network, lastFourDigits)) { mostrar aviso; exigir alias editable } }`, que es lo mismo que hace CardFormScreen. Después, borrar CardFormScreen (ACC2-15).

#### [ACC2-07] Controles visibles que no hacen nada en la pantalla de tasas
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/RateCatalogScreen.kt:169-174 (Compartir y Ayuda con `onClick` vacío), :389 y :589-646 (switch "Aplicar en próximas simulaciones"), :1405-1432 ("Ver tarifario oficial")
- **Qué está mal:** el switch solo cambia un `remember` local que nadie lee. El guardado siempre persiste la TEA y la usa el simulador. Los iconos Compartir y Ayuda, y el botón del tarifario, son decorativos pero TalkBack los anuncia como botones.
- **Escenario de fallo:** el usuario apaga "Aplicar en próximas simulaciones" y guarda → la TEA se usa igual en el simulador de cuotas, al contrario de lo que dice el control.
- **Fix mínimo:** borrar el switch, los dos `IconButton` y la tarjeta-botón (o cablearlos si producto los quiere).
- **Ahorro estimado:** 70 líneas

#### [ACC2-08] La TEA se interpreta con reglas distintas al crear la tarjeta y en la pantalla de tasas
- **Severidad:** media · **Categoría:** fusionar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** UnifiedInstrumentFormScreen.kt:495-498 (`MoneyInputParser`, sin tope) frente a RateCatalogScreen.kt:244-247, :391, :696-702 (`toDoubleOrNull` + `roundToInt`, tope 1000%)
- **Qué está mal:** el mismo dato (TEA en bps) se valida con dos reglas. En el alta se acepta hasta `Int.MAX_VALUE` bps y se rechazan 3 decimales. En tasas se acepta 0 a 1000% y se redondean 3 decimales.
- **Escenario de fallo:** en el paso 3 el usuario escribe "4550" queriendo decir 45.50 → se guarda 4550% sin aviso. La misma entrada en la pantalla de tasas se rechaza con "Ingresa una TEA entre 0% y 1000%". Y "45.555" se rechaza en el alta, pero en tasas se guarda como 45.56%.
- **Fix mínimo:** crear una sola función `parseTeaBps(text): Int?` (por ejemplo, en RateCatalogContext.kt, que ya tiene `formatTeaDraft`), con `MoneyInputParser` y tope 100000, y usarla en los tres sitios.
- **Ahorro estimado:** 10 líneas

#### [ACC2-09] El alta rechaza lo que la edición acepta: día de corte igual al de pago y línea 0
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** UnifiedInstrumentFormScreen.kt:460-486 (línea > 0, corte ≠ pago) frente a CreditCardTermsSheet.kt:71-74 (línea ≥ 0, sin regla corte ≠ pago)
- **Qué está mal:** la misma regla de la tarjeta se aplica distinto en dos sitios. El dominio (`CreditCard.init`) acepta los dos casos.
- **Escenario de fallo:** el alta con corte 5 y pago 5 muestra el error "Elige días distintos". Si el usuario crea la tarjeta con 5/20 y luego la edita a 5/5, se guarda. Con línea 0 pasa lo mismo: el alta pide "> 0" y la edición guarda S/ 0.00, y la utilización queda en 0% para siempre.
- **Fix mínimo:** decidir la regla (ver Dudas) y aplicarla en un solo helper usado por los dos formularios.

#### [ACC2-10] El editor de clasificación acumula colectores y puede mezclar datos de otro perfil
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/MovementClassificationEditorViewModel.kt:64-72 y :76-111
- **Qué está mal:** `observeData` lanza dos corrutinas nuevas en `viewModelScope` en cada emisión `Available` de `localAccess`. `collectLatest` cancela su bloque, pero no estos hijos, porque se lanzan en otro scope. Los colectores viejos siguen escribiendo en `_uiState`.
- **Escenario de fallo:** con el editor abierto, la sesión remota pasa de Absent a Present (nuevo valor `Available`) → hay 4 colectores. Si cambia el propietario local, los colectores del usuario anterior siguen poniendo sus categorías en `availableCategories`.
- **Fix mínimo:** en `observeData`, `coroutineScope { launch { … }; launch { … } }` y llamarlo como suspend dentro de `collectLatest`. Así se cancela al cambiar el acceso.

#### [ACC2-11] El editor de clasificación pisa la selección sin guardar del usuario
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** MovementClassificationEditorViewModel.kt:92-109
- **Qué está mal:** cada emisión del flujo de clasificación desde la base sobrescribe `selectedCategoryId` y el comercio, aunque el usuario ya haya elegido otros sin guardar.
- **Escenario de fallo:** el usuario cambia la categoría a "Supermercado" y, antes de tocar "Guardar", llega una sincronización o hidratación que actualiza la fila del movimiento → la selección vuelve a la categoría anterior y se guarda la vieja.
- **Fix mínimo:** aplicar la clasificación de la base solo la primera vez, con `.first()` en lugar de `collectLatest`, o con un flag `userEdited`.

#### [ACC2-12] El editor de clasificación ofrece categorías inválidas y oculta el comercio real
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** MovementClassificationEditor.kt:353-371; MovementClassificationEditorViewModel.kt:101-103
- **Qué está mal:** el diálogo lista todas las categorías activas, incluidas las de INGRESO y las bloqueadas por plan. El simulador sí las filtra (InstallmentSimulatorViewModel.kt:102-104). Además, el comercio ya asignado se muestra con el texto fijo "Comercio asignado" en vez de su nombre.
- **Escenario de fallo:** en un gasto, el usuario elige "Sueldo" (ingreso) → se guarda, porque el repositorio no compara tipos, y el gasto queda en la analítica de ingresos. Si elige una categoría bloqueada, falla con el mensaje en inglés "Category is blocked by the Free plan selection".
- **Fix mínimo:** filtrar `!isPlanLocked` y por tipo del movimiento, igual que el simulador. Resolver el nombre del comercio con el catálogo, o mostrar `provisionalText`.
- **Cross-módulo:** sí → CAT1 (el repositorio `updateMovementClassification` no valida tipo de categoría contra tipo de movimiento)

#### [ACC2-13] El guardado del asistente de crédito y la resolución de tasas por emisor no tienen test
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** UnifiedInstrumentFormScreen.kt:455-533; RateCatalogContextTest.kt (solo prueba emisores donde nombre = código)
- **Qué está mal:** el camino que crea tarjetas de crédito en producción (paso 3) no tiene test. Ese camino maneja el alias por producto, la TEA, la regla corte ≠ pago y `issuer = bank.name`. Los tests de formulario cubren cuentas, billeteras y la pantalla muerta CardFormScreen. `UnifiedInstrumentWalletAffiliationTest` crea `debitCards` (línea 51) y no lo usa.
- **Escenario de fallo:** ACC2-02 y ACC2-03 entraron sin que falle ningún test.
- **Fix mínimo:** un test de unidad en `RateCatalogContextTest` con `issuer = "Tarjeta CMR Falabella"` y un producto `BANCO_FALABELLA` que espere `Resolved`. Un test Compose del paso 3 con alias "1234" que espere un error en pantalla y que `registerCreditCard` no se llame.

#### [ACC3-03] El modo privado deja visible el porcentaje de uso
- **Severidad:** media · **Categoría:** seguridad · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:1613-1616, 1802-1811, 1821-1825
- **Qué está mal:** La pantalla oculta importes con el modo privado, pero conserva el porcentaje exacto y la barra proporcional de utilización de la tarjeta.
- **Escenario de fallo:** Al ocultar importes, una persona que mira la pantalla sigue viendo «Uso 80 %» y la barra correspondiente; el dashboard sí oculta esta información cuando está en modo privado.
- **Fix mínimo:** Ocultar porcentaje y progreso cuando LocalBalanceMasked esté activo, también en las propiedades de accesibilidad; probar una tarjeta con uso no nulo.

#### [ACC3-04] Las fechas de pago quedan congeladas al cambiar de día
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:1603-1610; app/src/main/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardSections.kt:547-548
- **Qué está mal:** Las fechas calculadas se recuerdan solo por el día configurado y la fecha actual se recuerda sin clave; no se actualizan al volver a primer plano o cruzar medianoche.
- **Escenario de fallo:** Si el detalle permanece abierto desde el día anterior al vencimiento, tras medianoche sigue diciendo que vence en un día; al pasar el vencimiento el dashboard puede mantener la fecha pasada.
- **Fix mínimo:** Actualizar la fecha local al reanudar la pantalla y al cambio de día, y usarla como clave de los cálculos; probar el cambio de fecha con reloj controlado.

#### [ACC3-05] El test de borradores prueba una validación que producción no usa
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/CreditPurchaseDraftValidation.kt:3-7; app/src/test/java/com/kipu/app/feature/accounts/presentation/detail/CreditPurchaseDraftValidationTest.kt:8-19; app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:408-424
- **Qué está mal:** La búsqueda de referencias encuentra el validador únicamente en su archivo y su test; la confirmación real usa otro camino. Por tanto los tests siguen verdes aunque se rompa la validación real de categoría.
- **Escenario de fallo:** Si el simulador permite confirmar sin categoría, este test aislado sigue pasando y el `requireNotNull` del callback puede lanzar una excepción.
- **Fix mínimo:** Probar la confirmación real con categoría ausente y borrar el helper y test aislados si no se conectan al flujo real.

#### [ACC3-06] El test de archivados pasa sin comprobar la condición archivada
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardEmptyStatePolicyTest.kt:45-56
- **Qué está mal:** Ambos casos pasan un DashboardUiState sin dashboardData; la función devuelve false por ese motivo antes de evaluar las listas archivadas.
- **Escenario de fallo:** Si se eliminan las comprobaciones de cuentas y tarjetas archivadas, este test sigue pasando y una cuenta archivada podría mostrarse como primera visita sin detectarlo.
- **Fix mínimo:** Pasar `dashboardData = emptyDashboard()` en ambos casos y mantener las listas archivadas como única diferencia.

#### [ACC3-07] Todas las compras aparecen como una sola cuota
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:2544-2548
- **Qué está mal:** La etiqueta de cada compra de crédito está fijada a «1 cuota» sin consultar sus condiciones.
- **Escenario de fallo:** Una compra confirmada a 12 cuotas aparece en movimientos con la etiqueta «1 cuota».
- **Fix mínimo:** Mostrar el número real asociado a la compra o eliminar la etiqueta si no está disponible; probar una compra a 12 cuotas.

#### [ACC3-08] El botón de eliminación permanente solo intenta archivar
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:726-729, 2062-2083, 938-954
- **Qué está mal:** En una tarjeta ya archivada, «Eliminar tarjeta permanentemente» llama a la acción de archivar y abre una confirmación que promete conservar el historial.
- **Escenario de fallo:** Al pedir borrar una tarjeta archivada aparece «¿Archivar la tarjeta?» y al confirmar se intenta archivarla de nuevo, sin eliminarla.
- **Fix mínimo:** Quitar el botón si no existe borrado permanente; si es un requisito aprobado, conectarlo a su operación con confirmación precisa. Probar la acción de una tarjeta archivada.

#### [ACC3-09] La deuda total se etiqueta como facturada
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:1705-1719
- **Qué está mal:** La pantalla muestra la deuda actual junto a «Facturado» sin calcular qué parte pasó por el corte.
- **Escenario de fallo:** Una compra recién registrada antes del corte aumenta la deuda actual y se presenta como facturada aunque pertenezca al ciclo abierto.
- **Fix mínimo:** Quitar «Facturado» o mostrar el importe facturado calculado del ciclo; probar una compra previa al corte.

#### [ACC3-10] El importe del borrador queda expuesto en modo privado
- **Severidad:** media · **Categoría:** seguridad · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt:365-378
- **Qué está mal:** El campo de importe de una compra se muestra como texto normal y no consulta `LocalBalanceMasked`, a diferencia del campo de corrección de saldo.
- **Escenario de fallo:** Con importes ocultos, al escribir 250.00 en «Nueva compra» la cifra queda visible en pantalla y en la semántica del campo.
- **Fix mínimo:** Aplicar el tratamiento privado de `OpeningBalanceCorrectionSheet`; probar texto y semántica con privacidad activa.

#### [ACC4-01] Árbol de productos de crédito bloquea el plegado si hay un producto seleccionado o si el banco tiene una sola red/nivel
- **Severidad:** media (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt:1439,1486
- **Qué está mal:** En `CalmEmeraldCreditProductTree`, las condiciones de apertura de grupos evalúan `val netOpen = netKey in expanded.value || hasSelection || byNetwork.size == 1` y `val tierOpen = tierKey in expanded.value || tierHasSelection || byTier.size == 1`. Si un producto está seleccionado (`hasSelection == true`), o si la entidad emisora tiene una sola red (como Banco Falabella CMR que solo tiene Visa) o un solo nivel, la expresión booleana es invariablemente `true`. Al tocar el encabezado para contraer el grupo, `toggle()` añade o elimina la clave del conjunto `expanded.value`, pero el `|| hasSelection` o `|| byNetwork.size == 1` anula la acción y mantiene la rama abierta. El usuario queda completamente imposibilitado de plegar la sección.
- **Escenario de fallo:** El usuario selecciona una tarjeta o abre un banco con una sola red (ej. Banco Falabella CMR) → pulsa el encabezado "Visa" para contraer la lista y despejar la pantalla → la lista no responde y permanece expandida.
- **Fix mínimo:** Inicializar el conjunto `expanded` con las claves seleccionadas o iniciales, y determinar `netOpen` únicamente por la presencia de `netKey` en `expanded` (o usar un conjunto de claves explícitamente colapsadas).
- **Verificación (Fable):** Defecto de UX (no se puede plegar), no de datos.

#### [ACC4-02] Vinculación de débito en edición de cuenta bloquea a BanBif y lo degrada a 'Otra entidad'
- **Severidad:** media (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt:594-601
- **Qué está mal:** En `registerDebitForEditedAccount`, la resolución del banco excluye explícitamente a BanBif: `bankChoices.firstOrNull { it.accountPreset == preset && it.code != "BANBIF" } ?: bankChoices.first { it.code == "OTHER" }`. Esto se introdujo como parche porque BanBif y "Otra entidad" comparten `AccountPreset.GENERIC` en `InstrumentFormModels.kt:93,104`. En consecuencia, si un usuario edita una cuenta de ahorros BanBif e ingresa los 4 dígitos para asociarle su tarjeta de débito, el selector descarta BanBif y cae al fallback `OTHER`. La tarjeta se persiste con `issuer = "Otra entidad"`, `preset = CardPreset.GENERIC` y `stylePresetId = null` (ya que `"OTHER"` no existe en `CardStylePresets`), perdiendo el arte oficial de `banbif-debit-classic`.
- **Escenario de fallo:** Usuario con cuenta BanBif edita la cuenta e ingresa "4321" en los últimos dígitos de débito → guarda cambios → la tarjeta de débito se crea como "Otra entidad" con estilo genérico Kipu verde azulado, perdiendo la marca oficial BanBif y su arte vectorial verificado.
- **Fix mínimo:** Asignar un `accountPreset = AccountPreset.BANBIF` propio a BanBif en `InstrumentFormModels.kt` y eliminar el filtro excluyente `&& it.code != "BANBIF"` en `AccountsViewModel.kt`.
- **Verificación (Fable):** `it.code != "BANBIF"` explícito. Decidido el 2026-11-11: incluir BanBif y elegir el banco por su código (§8.13).

#### [ACC4-03] Vinculación de débito en edición asigna erróneamente red VISA a tarjetas Mastercard
- **Severidad:** media (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt:598
- **Qué está mal:** `registerDebitForEditedAccount` construye la instancia de `DebitCard` con la red cableada fija: `network = com.kipu.app.feature.accounts.domain.model.CardNetwork.VISA`. Entidades emisoras cuyo débito estándar en Kipu es Mastercard (como Scotiabank con `CardPreset.SCOTIABANK_MASTERCARD` o Ripley con `CardPreset.RIPLEY_MASTERCARD`, definidos en `InstrumentFormModels.kt:72,127`) son registradas forzosamente como VISA.
- **Escenario de fallo:** Usuario con cuenta Scotiabank o Ripley ingresa dígitos de débito en la pantalla de edición de cuenta → la tarjeta de débito vinculada se guarda con red VISA en Room y metadatos de sincronización, contradiciendo el contrato del banco.
- **Fix mínimo:** Derivar la red del `cardPreset` del banco: `network = if (bank.cardPreset.name.contains("MASTERCARD")) CardNetwork.MASTERCARD else CardNetwork.VISA`.
- **Verificación (Fable):** Red `VISA` fija al vincular débito desde la edición.

#### [ACC4-04] Gestos de arrastre 3D en InstrumentCardPreview bloquean el desplazamiento vertical en formularios y saturan corrutinas
- **Severidad:** media (el worker dijo alta) · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/components/InstrumentCardPreview.kt:107-136
- **Qué está mal:** `detectDragGestures` ejecuta `change.consume()` incondicionalmente en cada movimiento del puntero para calcular el brillo e inclinación 3D. Al estar embebida en formularios con scroll vertical (`AccountFormScreen`, `CardFormScreen`, `DebitAccountSummary`), cualquier gesto vertical iniciado sobre la tarjeta es interceptado y consumido, impidiendo al usuario deslizar la pantalla hacia abajo. Además, el callback ejecuta `scope.launch { rotationY.snapTo(...); rotationX.snapTo(...) }` en cada evento de arrastre (hasta 120 fps), disparando cientos de corrutinas concurrentes efímeras para actualizar valores que no requieren suspensión.
- **Escenario de fallo:** En `CardFormScreen` en un teléfono de pantalla media o pequeña, el usuario intenta deslizar la pantalla apoyando el pulgar sobre la tarjeta preview superior → la pantalla no se desplaza porque el gesto es interceptado y consumido por la tarjeta.
- **Fix mínimo:** Consumir el cambio únicamente cuando el desplazamiento sea predominantemente horizontal (`abs(change.position.x - lastX) > abs(change.position.y - lastY)`), y actualizar las rotaciones mediante estado mutable simple (`mutableFloatStateOf`) en lugar de despachar corrutinas en cada frame táctil.
- **Verificación (Fable):** Afecta al scroll cuando el gesto empieza sobre la tarjeta; no satura corrutinas de forma medible.

#### [ACC4-05] Pérdida de estado UI en árbol de productos de crédito ante rotación de pantalla
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt:1426
- **Qué está mal:** En `CalmEmeraldCreditProductTree`, el estado de ramas expandidas se declara como `val expanded = remember { mutableStateOf(setOf<String>()) }`. Al usar `remember` simple en vez de `rememberSaveable`, el estado se destruye ante rotaciones de pantalla, cambios de tamaño de ventana o cambios entre tema claro y oscuro.
- **Escenario de fallo:** El usuario expande la red "Mastercard" y el nivel "Black" en el selector de tarjetas → gira el teléfono o cambia el modo oscuro → el árbol colapsa a su estado inicial, perdiendo la posición de navegación del usuario.
- **Fix mínimo:** Sustituir `remember { mutableStateOf(...) }` por `rememberSaveable { mutableStateOf(setOf<String>()) }`.

#### [ACC4-06] Corrupción de codificación en mensajes de error de notificaciones de crédito
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt:132,137
- **Qué está mal:** El literal de cadena contiene un carácter corrupto `?` en lugar de la vocal con tilde `é`: `_creditNotificationsError.value = "No se pudieron cargar los avisos de crédito."`. Se repite tanto en la verificación de resultado fallido como en el bloque `catch`.
- **Escenario de fallo:** Al fallar la consulta de avisos de sobregiro o vencer el timeout de 10 segundos, la interfaz de usuario muestra textualmente: "No se pudieron cargar los avisos de crédito.".
- **Fix mínimo:** Corregir la cadena a `"No se pudieron cargar los avisos de crédito."` o extraerla a un recurso en `strings.xml`.

#### [ACC4-07] Carreras concurrentes en refreshCreditUtilizationNotifications y estado de carga desincronizado
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt:121-141,144-157
- **Qué está mal:** `refreshCreditUtilizationNotifications` y `loadCreditProductCatalog` lanzan corrutinas en `viewModelScope` sin cancelar el trabajo previo en vuelo (`Job`). Si el usuario pulsa reintentar varias veces o navega con rapidez, múltiples peticiones concurrentes compiten; una petición lenta anterior puede finalizar después de una reciente y sobreescribir el estado con datos viejos o marcar `_creditNotificationsSettled = true` prematuramente. Además, en `loadCreditProductCatalog`, `_creditCatalogLoading.value = false` no se protege en un bloque `finally`, por lo que una cancelación de corrutina deja la bandera de carga en `true` permanentemente.
- **Escenario de fallo:** El usuario pulsa reintentar en el dashboard con conexión inestable → la primera petición falla después de que la segunda ya había tenido éxito → `_creditNotificationsError` se sobreescribe con el error tardío y oculta las alertas válidas.
- **Fix mínimo:** Almacenar `private var notificationsJob: Job? = null` y `private var catalogJob: Job? = null`, cancelando el job anterior al iniciar una nueva recarga, y ubicar el reset de carga dentro de un bloque `finally`.

#### [ACC4-08] Paréntesis de cierre huérfano en resumen de utilización de tarjeta de crédito
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/components/CreditCardSummaryCard.kt:416
- **Qué está mal:** En la fila de utilización de la línea de crédito, tras mostrar el importe de la línea mediante `MoneyText`, se renderiza un componente `Text(text = ")", ...)` sin que exista ningún paréntesis de apertura en los textos precedentes.
- **Escenario de fallo:** En `CreditCardSummaryCard`, la etiqueta de utilización muestra literalmente en pantalla: `"Uso de línea total de crédito: S/ 5,000.00)"`.
- **Fix mínimo:** Eliminar el componente `Text(text = ")", ...)` en las líneas 415-419.

#### [ACC4-09] Componente obsoleto y no referenciado en producción: CreditCardSummaryCard
- **Severidad:** media · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/components/CreditCardSummaryCard.kt:1-498
- **Qué está mal:** `CreditCardSummaryCard` (498 líneas) implementaba la ficha de resumen de tarjeta de crédito para la US3 / T042. Durante el rediseño Calm Emerald, `DashboardScreen.kt` implementó `DashboardCreditCardRow` de forma independiente, dejando a `CreditCardSummaryCard` sin ningún llamador en el código productivo de la app (solo lo invocan una preview temática y dos tests de instrumentación). Mantiene además una variable inútil `val nextBilling = remember(card.billingDay) { CreditCalculations.calculateNextDate(card.billingDay) }` en la línea 64 que nunca se lee en ninguna parte.
- **Escenario de fallo:** Modificaciones en las reglas visuales o de negocio de tarjetas de crédito no impactan a `CreditCardSummaryCard` y viceversa, manteniendo medio millar de líneas de código y pruebas desacopladas de lo que realmente ve el usuario en producción.
- **Fix mínimo:** Si `DashboardCreditCardRow` es la implementación definitiva de Calm Emerald, eliminar `CreditCardSummaryCard.kt` y actualizar los tests de etiquetas para inspeccionar el componente productivo. Si se busca unificar bajo la propuesta HI-08, reemplazar `DashboardCreditCardRow` por `CreditCardSummaryCard` en `DashboardScreen.kt`.
- **Ahorro estimado:** 498 líneas
- **Verificación (Fable):** `CreditCardSummaryCard(` solo en su archivo, su preview y dos androidTest.

#### [ACC4-11] Discrepancia en nombres de productos entre CardArt y CardStylePresets rompe la resolución de estilo en forProduct
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/components/CardStylePresets.kt:170-183
- **Qué está mal:** `CardStylePresets.forProduct` busca coincidencias exactas entre el nombre suministrado y `preset.productName` o `preset.aliases`. `CardStylePresets.all` inicializa `verifiedBankPresets` dejando `aliases = emptySet()` para todos los presets bancarios. Al mismo tiempo, los nombres en `CardStylePresets` omiten el prefijo de la entidad (ej. `"CMR Visa"`, `"Visa Singular Signature"`, `"Tarjeta de Débito BBVA"`), mientras que `CardArtCatalog`, catálogos externos o el alias del usuario suelen incluirlo (`"Banco Falabella CMR Visa"`, `"Scotiabank Visa Singular Signature"`, `"Débito BBVA"`). Al no haber coincidencia exacta ni alias registrados, `forProduct` devuelve `null`.
- **Escenario de fallo:** En `UnifiedInstrumentFormScreen:397` o `AccountDetailScreen:1026`, cuando una tarjeta llega con su nombre oficial de catálogo `"Banco Falabella CMR Visa"`, `CardStylePresets.forProduct("BANCO_FALABELLA", "Banco Falabella CMR Visa")` retorna `null`, perdiendo el estilo y vector oficial.
- **Fix mínimo:** Incluir los nombres completos de `CardArtCatalog` como `aliases` en la definición de cada preset bancario o normalizar eliminando el prefijo del nombre de la entidad emisora antes de comparar en `forProduct`.

#### [ACC4-18] Falta de tests de robustez en parser y validación de TEA en borradores de compra a crédito
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/components/CreditPurchaseDraft.kt:57-62
- **Qué está mal:** La conversión de TEA utiliza `toBigDecimal().movePointRight(2).intValueExact()`. No existen pruebas unitarias en `test/` que cubran entradas con más de 2 decimales, formatos científicos, comas decimales europeas o números negativos para validar que no lancen excepciones no controladas fuera del `runCatching`.
- **Escenario de fallo:** Una modificación en `validateCreditPurchaseDraft` que altere el manejo de excepciones numéricas podría propagar `ArithmeticException` o `NumberFormatException` a la UI sin que ningún test unitario falle.
- **Fix mínimo:** Añadir un test unitario en `CreditPurchaseDraftValidationTest` con casos de prueba para: `"15.255"`, `"1e2"`, `"-5"`, `"12,5"`, y valores límites en 0 bps y 50_000 bps.

#### [ACC4-19] Falta de tests para vinculación de tarjetas de débito de entidades distintas a BCP en edición de cuenta
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/feature/accounts/presentation/EditAccountDebitRegistrationTest.kt:14-28
- **Qué está mal:** La suite `EditAccountDebitRegistrationTest` únicamente comprueba el escenario de una cuenta BCP. No existen tests para BanBif (donde ocurre la exclusión indebida), Scotiabank (donde la red se corrompe a VISA), ni para cuentas bancarias sin preset definido (OTHER).
- **Escenario de fallo:** Los defectos ACC4-02 y ACC4-03 no fueron detectados por las pruebas existentes porque sólo se probó la entidad BCP con datos estáticos favorables.
- **Fix mínimo:** Añadir casos de prueba para `AccountPreset.SCOTIABANK` (afirmando que no fuerza VISA si el banco es Mastercard) y para `AccountPreset.GENERIC` / BanBif comprobando que se preserva la entidad correcta.

#### [ACC4-20] Inyección de ObserveCardInstallments con valor por defecto hardcodeado en AccountsViewModel
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt:95-96
- **Qué está mal:** El constructor de `@HiltViewModel class AccountsViewModel` define un valor por defecto que instancia directamente el caso de uso: `private val observeCardInstallmentsUseCase: ObserveCardInstallments = ObserveCardInstallments(financialInstrumentsRepository, java.time.Clock.systemUTC())`. En Hilt, el inyector no utiliza argumentos por defecto de Kotlin a menos que exista un Provider, y la instanciación manual salta el grafo de dependencias de Dagger fijando un reloj UTC no configurable en lugar de inyectar la instancia provista por Hilt.
- **Escenario de fallo:** Si se configura un reloj simulado para pruebas de integración o Hilt cambia la provisión de `ObserveCardInstallments`, `AccountsViewModel` mantiene su instancia por defecto ligada a `Clock.systemUTC()`.
- **Fix mínimo:** Eliminar el valor por defecto en el constructor de `AccountsViewModel` e inyectar `observeCardInstallmentsUseCase: ObserveCardInstallments` como dependencia regular obligatoria en el `@Inject constructor`.

#### [ACC5-06] La compra rechazada permanece publicada en el historial de tarjeta
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:689-705; app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:1387-1404,1934-1943
- **Qué está mal:** El rechazo anula la transacción y las cuotas, pero deja POSTED el `financial_movements` creado para esa compra. El historial de tarjeta se alimenta de esa tabla y no consulta el estado anulado de la transacción.
- **Escenario de fallo:** Una compra de S/100 recibe REJECTED. La deuda se compensa a cero, pero `observeMovementsByCard` continúa entregando la compra como POSTED. El test instrumentado comprueba cuotas y ledger, pero no esta proyección.
- **Fix mínimo:** Actualizar también el estado de la proyección de movimientos por operación dentro de la transacción de rechazo. Extender el test de rechazo para comprobar que la compra ya no se entrega como publicada.

#### [ACC5-07] Fallar al programar sync devuelve error después de guardar
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:498-577,701-738,782-842
- **Qué está mal:** Varias escrituras incluyen `scheduleSync` en el mismo `runCatching` que devuelve el resultado, aunque Room ya hizo commit. Crear cuentas y corregir aperturas ya separan correctamente esas dos operaciones mediante `scheduleSyncBestEffort`, pero las altas de tarjetas y sus ediciones no.
- **Escenario de fallo:** WorkManager lanza una excepción al programar una tarjeta recién guardada. El llamador recibe fallo y permite reintentar; la tarjeta y su comando ya existen, por lo que el reintento puede fallar por duplicado o registrar otra operación distinta. La UI informa que no se guardó algo que sí está guardado.
- **Fix mínimo:** Reutilizar `scheduleSyncBestEffort` después de un commit exitoso en las demás escrituras, propagando cancelación. Test mínimo equivalente al de OpeningAdjustmentRetryRoomTest, aplicado a registrar tarjeta.

#### [ACC5-08] KEEP puede perder el aviso del último comando
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:67-70,125-126,151-153
- **Qué está mal:** El último conteo de pendientes y la finalización del worker no son atómicos con las escrituras del repositorio. `ExistingWorkPolicy.KEEP` descarta una programación si el worker anterior todavía está ejecutándose, aunque ya haya decidido devolver éxito.
- **Escenario de fallo:** El worker lee cero pendientes y decide success; antes de terminar, el usuario guarda otro comando. Su scheduleSync encuentra el trabajo RUNNING y KEEP descarta el aviso. El trabajo termina y el comando queda sin ejecución hasta otra acción que vuelva a programarla.
- **Fix mínimo:** Usar una política que conserve una ejecución posterior, por ejemplo APPEND_OR_REPLACE, y probar la ventana entre el último conteo y la finalización. No basta con otro conteo sin cambiar la programación, porque desplaza la misma carrera.

#### [ACC5-09] Borrar una tarjeta de crédito aún no enviada rompe su registro pendiente
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:980-1006; app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:201-207
- **Qué está mal:** El borrado elimina inmediatamente la fila Card. Al procesar un REGISTER_CARD de crédito, el worker exige obtener su cuenta de pasivo de esa fila, aunque el DTO persistido ya lleva accountId.
- **Escenario de fallo:** Registrar tarjeta de crédito offline y llamar DeleteUnusedCard antes del primer sync: el registro recibe el error local «Credit card liability account is missing» y queda permanente; el DELETE que lo sigue queda bloqueado. La API de borrado existe en dominio; no encontré un llamador de UI, por eso la severidad es media.
- **Fix mínimo:** Para una tarjeta creada y eliminada sin ningún envío, cancelar ambas operaciones y limpiar el pasivo que no tenga uso; cuando hubo envío, conservar el dato necesario para completar el protocolo. Test mínimo: registrar, borrar offline y sincronizar sin dejar una cadena pendiente permanente.

#### [ACC2-14] El pago de tarjeta valida contra un saldo viejo de la cuenta origen
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/PayCardDialog.kt:67, :193-201
- **Qué está mal:** `selectedAccount` se recuerda sin clave y guarda el `AccountWithBalance` del momento en que se eligió. Si el dashboard se recarga, el control "Fondos insuficientes" sigue usando el saldo anterior. Si el diálogo se abre antes de cargar las cuentas, no queda ninguna preseleccionada.
- **Escenario de fallo:** el diálogo está abierto, entra por sincronización un gasto de la otra cuenta del usuario que baja el saldo de S/ 500 a S/ 100, y el usuario paga S/ 300 → la UI no lo frena (depende de que el caso de uso lo rechace).
- **Fix mínimo:** guardar solo el id (`var selectedAccountId by remember { … }`) y resolver `matchingAccounts.firstOrNull { it.account.id == selectedAccountId } ?: matchingAccounts.firstOrNull()` en cada recomposición.

#### [ACC2-15] AccountFormScreen y CardFormScreen ya no se usan
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/AccountFormScreen.kt:59-304; CardFormScreen.kt (completo); app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/instruments/CardFormScreenTest.kt (completo)
- **Qué está mal:** la navegación usa solo `UnifiedInstrumentFormScreen` (AccountsNavigation.kt:183, :201). `AccountFormScreen` no tiene llamadores, y `CardFormScreen` solo lo llama su test. De AccountFormScreen.kt solo se usa `parseAmountToMinorUnits` (PayCardDialog.kt:184).
- **Escenario de fallo:** el aviso de duplicados y los textos de cupo "1 de los 4 cupos" existen solo en estas pantallas, y sus tests pasan mientras la pantalla real no lo hace (ver ACC2-06).
- **Fix mínimo:** borrar los dos archivos y el test. Llamar a `MoneyInputParser.parseMinorUnits` directo desde PayCardDialog, o mover `parseAmountToMinorUnits` a PayCardDialog.kt.
- **Ahorro estimado:** 900 líneas
- **Verificación (Fable):** `AccountFormScreen(` y `CardFormScreen(` solo aparecen en su propio archivo y en `CardFormScreenTest`.

#### [ACC2-16] Código muerto dentro de UnifiedInstrumentFormScreen
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** UnifiedInstrumentFormScreen.kt:1298-1375 (`TreeBranchItem`), :1377-1461 (`BranchProductCard`), :1465-1486 (`ReferencialTeaCard`), :1530 (`percentage`), :113-119 (colores solo usados por esos composables), :421-453 (`parsedAmountForValidation`, `cycleIsValid`, `lastFourIsValid`, `cardBackground`, `previewStylePreset`, `cardForeground`, `previewTitle`), :258-262 y :296-300 (estado que solo se escribe), :313 y :318 (`cycleError` y su requester, nunca mostrados), :561-564, :580-584 y :636-656 (rama CREDIT_CARD de `handleSubmit`, inalcanzable porque CREDIT_CARD nunca llega a FORM_OTHER), :208 (`onNavigateToRecordConsumption`, nunca invocado), :1503-1519 (`toCardNetwork` y `networkFromCatalog` hacen lo mismo)
- **Qué está mal:** hay restos del diseño anterior que hay que leer y compilar, y que confunden. Por ejemplo, la rama de crédito de `handleSubmit` no aplica la regla corte ≠ pago y parece una segunda verdad.
- **Escenario de fallo:** quien corrija ACC2-02 puede arreglar la rama muerta de `handleSubmit` y dejar rota la real, `handleSaveCreditCard`.
- **Fix mínimo:** borrar todo lo listado y el argumento `onNavigateToRecordConsumption` en AccountsNavigation.kt:191-194 y :209-212.
- **Ahorro estimado:** 300 líneas

#### [ACC2-17] "Recordar comercio" en el simulador solo vive en memoria
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** InstallmentSimulatorViewModel.kt:73, :141, :175, :197, :210
- **Qué está mal:** `rememberedCategories` es un mapa del ViewModel, que tiene clave por candidato. Se pierde al cerrar el simulador, así que el interruptor "Recordar" del selector no tiene efecto entre compras.
- **Escenario de fallo:** el usuario crea la categoría "Gimnasio" con "Recordar" activado para "SmartFit" y confirma. En la siguiente compra en SmartFit no se sugiere nada.
- **Fix mínimo:** borrar el mapa y sus 4 usos. Si producto quiere recordar, la vía es la preferencia persistida de comercio (`ResolvePreferredCategory`, CAT1).
- **Ahorro estimado:** 6 líneas

#### [ACC2-18] UnifiedInstrumentFormScreen mezcla dos flujos en una función de ~1100 líneas
- **Severidad:** baja · **Categoría:** dividir · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** UnifiedInstrumentFormScreen.kt:203-1293
- **Qué está mal:** el asistente de crédito (3 pasos, `handleSaveCreditCard`) y el formulario de cuenta, billetera o efectivo (`handleSubmit`) comparten unas 60 variables de estado y dos validadores casi iguales. Por eso aparecen ACC2-02 (valida uno y el otro no) y ACC2-16.
- **Escenario de fallo:** ver ACC2-02. La validación del alias se añadió en `handleSubmit` y no en `handleSaveCreditCard`.
- **Fix mínimo:** después de ACC2-16, extraer `CreditCardWizard(...)` con su propio estado. El composable raíz solo elige la ruta.
- **Ahorro estimado:** 0 líneas (gana legibilidad, no tamaño)

#### [ACC2-19] Campos de modelo sin uso
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** InstrumentFormModels.kt:21 (`logoAcronym`, más las 10 asignaciones), :30-31 (`CatalogProductChoice.familyId/familyLabel`)
- **Qué está mal:** ningún archivo de `app/src` lee estos campos.
- **Escenario de fallo:** ninguno funcional. Son líneas que hay que mantener al añadir bancos.
- **Fix mínimo:** borrarlos.
- **Ahorro estimado:** 13 líneas Dejé fuera 4 hallazgos menores: los colores fijos amarillo-BCP en `CardContextHeader` para cualquier emisor, `personalTeaSaved` acoplado al prefijo "TEA personal" del mensaje, el `selectedAccount` inicial de CardFormScreen (pantalla muerta) y un mensaje de error en inglés que viene del repositorio.

#### [ACC4-10] Código muerto: AlertTogglePill en CalmEmeraldInstrumentComponents
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt:1761-1806
- **Qué está mal:** La función composable privada `AlertTogglePill` (46 líneas) no es llamada desde ninguna parte de `CalmEmeraldInstrumentComponents.kt` ni del resto del proyecto. Quedó huérfana cuando se descartaron los chips interactivos de umbrales 50/80/100 (como documenta el comentario en líneas 1104-1107).
- **Escenario de fallo:** Código muerto residual que incrementa el peso del archivo sin aportar funcionalidad.
- **Fix mínimo:** Borrar la función `AlertTogglePill`.
- **Ahorro estimado:** 46 líneas

#### [ACC4-12] Inconsistencia en altura mínima de tarjeta con arte ID-1 en InstrumentCardPreview
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/components/InstrumentCardPreview.kt:105,146
- **Qué está mal:** La altura mínima se define como `min = if (art != null && stylePreset?.cardType == CardStyleType.DEBIT) 0.dp else 160.dp`. Ambas tarjetas (débito y crédito) usan `CardArtFace` con la misma proporción ID-1 (`aspectRatio = 1.586f`). Si una tarjeta de crédito con arte se renderiza en un contenedor de ancho inferior a 254dp (donde la altura proporcional natural es menor a 160dp), la `Card` contenedora se expande forzadamente a 160dp mientras que la imagen interna se ajusta a su proporción, generando espacio vertical vacío asimétrico sólo para crédito.
- **Escenario de fallo:** Al mostrar una tarjeta de crédito con arte en un panel estrecho (ej. pantalla dividida de 220dp de ancho, donde la altura proporcional es ~138dp), el contenedor queda inflado a 160dp dejando 22dp de franja transparente innecesaria.
- **Fix mínimo:** Aplicar `min = if (art != null) 0.dp else 160.dp` sin discriminar por tipo de tarjeta.

#### [ACC4-13] BasicTextFields en fechas de tarjeta sin placeholder ni pista visual de entrada
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt:2024-2036,2075-2087
- **Qué está mal:** En `CalmEmeraldDateInputsCard`, los campos `billingDayInput` y `dueDayInput` son `BasicTextField` sin `decorationBox` ni placeholder. A diferencia de `CalmEmeraldCreditLimitInputCard` (que muestra "0.00") y `CalmEmeraldLastFourDigitsCard` (que muestra "0000"), los campos de día de corte y pago quedan totalmente vacíos e invisibles excepto por un cursor estrecho en un cuadro de 48dp de ancho.
- **Escenario de fallo:** El usuario entra al Paso 3 de alta de tarjeta de crédito y observa dos cajas en blanco bajo "Día de cierre" y "Día de pago" sin ninguna pista del formato esperado (ej. "DD" o "15").
- **Fix mínimo:** Añadir un `decorationBox` que muestre un placeholder tenue (ej. "15" y "05") cuando el campo esté vacío, consistente con los otros campos del paso 3.

#### [ACC4-14] Formateo manual ad-hoc de línea de crédito en CalmEmeraldSuccessBottomSheet en lugar de MoneyText
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt:2376-2377
- **Qué está mal:** En el bottom sheet de éxito al registrar tarjeta, la celda de línea total formatea el importe con concatenación manual: `if (LocalBalanceMasked.current) "••••" else "${if (card.currency == Currency.PEN) "S/" else "$"} ${formatMinorUnits(card.creditLimitMinorUnits)}"`, en lugar de invocar `MoneyText(Money(card.creditLimitMinorUnits, card.currency))` que ya implementa tipografía, colores, formato y soporte para enmascaramiento.
- **Escenario de fallo:** Si se modifica el símbolo o formato de privacidad en `MoneyText`, esta pantalla mostrará un formato desalineado con el resto de la aplicación.
- **Fix mínimo:** Reemplazar el bloque `Text(...)` manual por `MoneyText(money = Money(card.creditLimitMinorUnits, card.currency), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))`.

#### [ACC4-15] Fila de cuotas de tarjeta omite desglosar el importe de intereses pese a verificar su existencia
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/accounts/presentation/components/CardInstallmentsSection.kt:271-286
- **Qué está mal:** `CardInstallmentRow` verifica `if (details.isNotEmpty() || item.interest.minorUnits > 0L)` para renderizar la fila inferior de detalles. Sin embargo, dentro de dicha fila únicamente se imprime `details.joinToString(" · ")` (descripción y fecha de corte) y `"Total: S/ total"`. El importe de los intereses de la cuota (`item.interest`) nunca se incluye en el texto visible, por lo que la condición `item.interest.minorUnits > 0L` no produce ninguna visualización del interés generado.
- **Escenario de fallo:** Una cuota con intereses confirmada (ej. S/ 100 de capital y S/ 10 de interés) sin descripción ni fecha de corte renderiza un espacio en blanco en la columna izquierda y el total a la derecha, sin que el usuario pueda saber que se le cobraron S/ 10 de interés en esa cuota.
- **Fix mínimo:** Si `item.interest.minorUnits > 0L`, incorporar a `details` el texto `"Interés: ${formatMoney(item.interest)}"`.

#### [ACC4-16] Harness obsoleto de captura de pantallas sin aserciones en androidTest
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/Issue48CopyScreenshotTest.kt:1-166
- **Qué está mal:** Este archivo no prueba componentes de producción ni contiene aserciones de regresión. Crea una pantalla composable interna sintética (`CopyComparisonScreen`) y exporta dos imágenes PNG al disco del dispositivo (`issue48-before.png` e `issue48-after.png`) que sirvieron como evidencia fotográfica temporal para el PR del Issue 48. Sigue ejecutándose en cada suite de pruebas instrumentadas consumiendo ciclos sin validar nada.
- **Escenario de fallo:** Tiempo de ejecución de pruebas instrumentadas desperdiciado en renderizar y escribir a disco dos imágenes de una UI sintética que no existe en la app real.
- **Fix mínimo:** Eliminar el archivo `Issue48CopyScreenshotTest.kt`.
- **Ahorro estimado:** 166 líneas

#### [ACC4-17] Sobrecarga de I/O en CardArtFaceTest por generación masiva de bitmaps
- **Severidad:** baja · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/components/CardArtFaceTest.kt:68-82,172-174
- **Qué está mal:** Durante la ejecución de `CardArtFaceTest`, se comprimen a PNG y escriben a almacenamiento 198 archivos de imagen en cada ejecución (78 imágenes en `verifiedBbvaAndScotiaFaces...` y 120 imágenes en `everyCatalogProductRendersFrontAndBackOnDevice`). Esto satura el disco en pruebas instrumentadas continuas.
- **Escenario de fallo:** Lentitud significativa en la suite de `androidTest` por operaciones de compresión de imágenes y escritura síncrona en disco que no son parte de las aserciones de prueba.
- **Fix mínimo:** Condicionar la exportación de archivos a un flag booleano o de depuración (`exportImages = false` por defecto en CI), preservando únicamente las aserciones semánticas y de diseño en memoria.

### Backend SQL (migraciones)

Porciones: SQL1 (Codex gpt-6.1-sol medium); SQL2 (Codex gpt-6.1-sol medium); SQL3 (Codex gpt-6.1-sol medium); SQL4 (Codex gpt-6.1-sol medium). Hallazgos: 35 (2 crítica, 14 alta, 19 media).

#### [SQL1-01] La segunda corrección de apertura suma dinero de más
- **Severidad:** crítica · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** supabase/migrations/20260921000000_ep_cta_baseline.sql:557–580
- **Qué está mal:** `record_opening_adjustment_v1` siempre revierte el `OPENING` original, aunque ya haya una corrección vigente. No busca el último ajuste sin reversar ni serializa las correcciones de la cuenta; su cuerpo no fue reemplazado en migraciones posteriores.
- **Escenario de fallo:** apertura de 10000 céntimos → corrección a 20000 → segunda corrección a 30000: los hechos quedan `10000 - 10000 + 20000 - 10000 + 30000 = 40000`, aunque el saldo corregido debe ser 30000. El trigger posterior `internal.post_opening_movement_ledger_v1` también contabiliza estos hechos, por lo que el error alcanza el ledger del servidor; Android ya calcula su reversión contra el ajuste vigente y diverge del servidor.
- **Fix mínimo:** bloquear la cuenta, localizar el hecho vigente sin reversión y revertir ese importe/identidad antes de insertar el nuevo ajuste. Mantener el vínculo del ajuste con la apertura original; al coordinarlo con el trigger de contabilización, asegurar que también se reconozcan reversiones de ajustes.
- **Verificación (Fable):** Leído `record_opening_adjustment_v1`: siempre revierte el OPENING original y no tiene redefinición posterior.

#### [SQL4-01] Anular una compra reembolsada deja dinero sin contrapartida
- **Severidad:** crítica · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** supabase/migrations/20261021200100_reconcile_movement_revision_contract.sql:125–130,307–375; supabase/migrations/20261024182114_ep_mov_refund_v1.sql:194–224
- **Qué está mal:** La revisión genérica excluye tarjetas, cuotas y el puntero antiguo de reembolso, pero no los enlaces `transaction_links/REFUND` del contrato nuevo. Puede revertir o reducir una compra estándar y conservar íntegro su reembolso; el parche final de 20261104051901 solo cambia la validación de categorías.
- **Escenario de fallo:** Una compra estándar resta S/100 y su reembolso completo suma S/100. Otro dispositivo envía una anulación pendiente de esa compra con su revisión todavía válida: el servidor suma S/100 de reversión y deja el reembolso confirmado, por lo que el saldo termina S/100 por encima del saldo anterior a la compra; también permite reducir la compra por debajo de lo ya reembolsado.
- **Fix mínimo:** Bajo el bloqueo existente de la compra, rechazar edición/anulación genérica cuando existan reembolsos válidos vinculados, con `OPERATION_SPECIALIZED`. Añadir un test compra → reembolso total → anulación atrasada que compruebe rechazo y saldo intacto.
- **Verificación (Fable):** Confirmado: el guard de `revise_or_void` rechaza anular el reembolso, pero no anular una compra estándar que tiene reembolsos. El reembolso tampoco se puede anular después: el saldo queda mal sin vía de corrección.

#### [SQL1-02] Un ajuste confirmado nunca aparece en el feed de sincronización
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** supabase/migrations/20260921000000_ep_cta_baseline.sql:565–590
- **Qué está mal:** la RPC escribe reversión y ajuste, pero no publica ningún cambio en `internal.sync_changes`, no actualiza la revisión de la cuenta y no actualiza su proyección de apertura. No hay un trigger posterior que publique estos cambios: el de `20261026013311_planning_remote_sync_activation.sql:37–67` solo escribe el ledger.
- **Escenario de fallo:** dos dispositivos ya sincronizaron una apertura de S/100; A la corrige a S/200 y recibe `APPLIED`. B solicita cambios posteriores a su cursor y no recibe ningún evento por esa corrección; conserva S/100 mientras el servidor ya contabiliza S/200. Una restauración desde el feed también recibe la apertura anterior.
- **Fix mínimo:** publicar dentro de la misma transacción una proyección versionada de la apertura corregida que incluya sus hechos de reversión/ajuste. Acordar su consumo con MOV2; reenviar únicamente el importe original de `accounts` no restaura esos hechos.
- **Verificación (Fable):** La RPC no escribe en `internal.sync_changes` ni sube la revisión.

#### [SQL1-03] Reintentar una corrección confirmada devuelve una violación de unicidad
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** supabase/migrations/20260921000000_ep_cta_baseline.sql:546–590
- **Qué está mal:** `record_opening_adjustment_v1` recibe `operation_id` e identidades estables de ambos movimientos, pero no consulta ni guarda un recibo de operación. El reintento vuelve a ejecutar los dos `INSERT`.
- **Escenario de fallo:** el servidor confirma el ajuste, pero se pierde la respuesta HTTP; el outbox vuelve a mandar exactamente el mismo comando. El primer `INSERT` colisiona con la clave primaria o con `(user_id, operation_id, operation_sequence)` y devuelve `23505`, en vez del éxito original; el cliente no puede conciliar la operación que ya se aplicó.
- **Fix mínimo:** guardar el resultado y el hash del contenido en `internal.command_receipts` y consultar ese recibo bajo un bloqueo por operación antes de escribir. Un reintento idéntico debe devolver el resultado guardado y un contenido diferente debe producir una colisión explícita.
- **Verificación (Fable):** Sin consulta de `command_receipts`: el reintento repite los INSERT.

#### [SQL1-04] Resolver un conflicto descarta la versión elegida
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20260923010000_fix_category_conflict_resolution.sql:11–30
- **Qué está mal:** `chosen_version` solo se valida; nunca se lee `local_version`/`remote_version` ni se aplica la versión seleccionada a la presentación o al estado de la categoría. Se marca el conflicto como resuelto sin resolver los datos, contrario a FR-018 de EP-CCO.
- **Escenario de fallo:** la presentación remota dice «Educación» y la versión local guardada en el conflicto dice «Universidad»; el usuario elige `LOCAL`. La RPC devuelve `RESOLVED`, pero la presentación sigue diciendo «Educación»; el siguiente pull conserva el valor que el usuario rechazó. El llamador Android solo envía la elección, no otro comando que aplique esa versión.
- **Fix mínimo:** bloquear el conflicto, aplicar el contenido elegido a la entidad correspondiente con su revisión y marcarlo resuelto en esa misma transacción. El test actual `category_merchant.test.sql:223–232` debe comprobar el valor resultante, además de `RESOLVED`.

#### [SQL1-05] Dos dispositivos pueden sobrescribir una presentación sin conflicto
- **Severidad:** alta · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20260922000000_ep_cco_baseline.sql:246–279
- **Qué está mal:** `update_category_presentation_v1` lee la revisión sin bloqueo y actualiza después sin comprobar la revisión en el `WHERE`. Dos llamadas pueden validar la misma revisión y ambas escribir la misma revisión siguiente; la migración de hardening posterior solo fija `search_path` y permisos, no cambia este cuerpo.
- **Escenario de fallo:** presentación en revisión 1; A quiere «Casa» y B quiere «Hogar», ambos con `expected_revision=1`. Ambos leen 1 antes de escribir; A guarda revisión 2 y B espera su `UPDATE`, luego sobrescribe con «Hogar», también revisión 2; ambas llamadas responden `UPDATED` y no se conserva ningún conflicto de A.
- **Fix mínimo:** añadir `FOR UPDATE` a la lectura de la presentación antes de comparar revisiones. Probar dos sesiones que intenten editar la misma revisión: una debe actualizar y la otra debe conservar ambas versiones como conflicto.

#### [SQL1-06] Un reintento de presentación se convierte en un conflicto ficticio
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20260922000000_ep_cco_baseline.sql:234–269
- **Qué está mal:** `update_category_presentation_v1` ignora la identidad de operación del comando y no conserva su resultado. Cada reintento compara nuevamente la revisión inicial contra una revisión que la propia operación ya incrementó.
- **Escenario de fallo:** una edición con `operation_id=X` y revisión esperada 1 se aplica como revisión 2; se pierde la respuesta. El mismo outbox reenvía X y el servidor crea un conflicto `OPEN`, aunque no hubo ninguna segunda edición; Android interpreta ese resultado como conflicto y detiene la sincronización normal de la edición.
- **Fix mínimo:** deduplicar por propietario e identidad de operación con hash y resultado persistido antes de comparar revisiones. El caso mínimo de test es repetir exactamente una actualización aplicada y comprobar que no aparece ningún conflicto ni otro incremento de revisión.

#### [SQL2-01] Borrar una tarjeta puede separarla de una compra concurrente
- **Severidad:** alta · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** `supabase/migrations/20260928110000_credit_card_pull_projection.sql:525–541`.
- **Qué está mal:** `delete_unused_card_v1` consulta el historial antes de bloquear la tarjeta. El `DELETE` puede esperar a una compra y después borrar la tarjeta sin repetir la comprobación; la relación de `transactions.card_id` usa `ON DELETE SET NULL` (`20260920000000_financial_core_baseline.sql:198`). La función de borrado no tiene reemplazo posterior.
- **Escenario de fallo:** el dispositivo A inicia la primera compra y mantiene bloqueada la tarjeta; B intenta borrarla y no ve la compra todavía sin confirmar. B supera ambos `EXISTS` y espera en el `DELETE`; A confirma, B continúa y elimina la tarjeta, dejando la compra con `card_id = NULL`, cuotas sin tarjeta localizable y el pasivo contable en la cuenta oculta. La compra vigente bloquea la tarjeta con `FOR UPDATE` en `20261101140000_credit_card_principal_quota_archive.sql:541–545`, pero el borrado toma ese bloqueo demasiado tarde.
- **Fix mínimo:** bloquear primero la tarjeta del dueño con `SELECT … FOR UPDATE`, y consultar ambos historiales después de adquirir el bloqueo, antes del `DELETE`. Añadir un caso con dos conexiones: compra pendiente de confirmar frente a borrado; tras confirmar la compra, el borrado debe devolver `CARD_HAS_HISTORY`.

#### [SQL2-02] La edición sigue bloqueando Premium y cuenta débito por separado
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** `supabase/migrations/20261003004348_s4_movement_revision_rpc_v1.sql:158–177`.
- **Qué está mal:** `internal.movement_instrument_locked_v1` mantiene el conteo antiguo de cuentas más tarjetas y no consulta el plan. Sigue siendo la definición final del helper y el RPC vigente de edición lo llama para cambiar cuentas (`20261021200100_reconcile_movement_revision_contract.sql:222–232`). El control compartido ya contempla Premium y grupos banco/débito/billetera, pero este helper lo duplica sin esas reglas.
- **Escenario de fallo:** un usuario Premium con cinco cuentas bancarias, sin selección Free, edita un gasto para moverlo de una cuenta a otra: recibe `INVALID_REFERENCE` aunque ambas sean válidas. También ocurre en Free con tres bancos y tres tarjetas de débito asociadas: son tres grupos permitidos, pero este helper cuenta seis instrumentos y bloquea el cambio.
- **Fix mínimo:** sustituir el cálculo duplicado por `private.is_plan_quota_resource_locked(p_user_id,'INSTRUMENTS',p_account_id)`. Añadir una edición entre cuentas con Premium y cinco bancos, y otra con tres grupos banco/débito; el test actual solo cubre el rechazo Free con cinco bancos (`supabase/tests/database/movements_revision_test.sql:94–114`).

#### [SQL3-01] Editar un gasto devuelve respaldo inexistente a las metas
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261018190000_ep_mov_opening_ledger.sql:125–137
- **Qué está mal:** `goal_confirmed_balance` solo suma asientos de transacciones `CONFIRMED`. Una edición válida deja el movimiento en `REVISED`, que sigue vigente y conserva su efecto financiero neto; el cálculo elimina todos sus asientos. La definición final de este helper sigue en este archivo.
- **Escenario de fallo:** apertura de 100000 centavos y gasto confirmado de 60000; se edita únicamente su nota y queda `REVISED` → el respaldo pasa de 40000 a 100000 y se pueden aceptar reservas por dinero ya gastado. El camino de edición asigna `REVISED` en `supabase/migrations/20261021200100_reconcile_movement_revision_contract.sql:373`; la prueba de saldos solo cubre `CONFIRMED`, `VOIDED` y eliminados (`goal_opening_ledger_test.sql:104–116`).
- **Fix mínimo:** incluir `REVISED` junto a `CONFIRMED`, sumando el neto del ledger existente. Añadir un caso que edite la nota de un gasto y compruebe que el respaldo no aumenta.

#### [SQL3-02] Reasignar reservas permite superar las dos metas activas Free
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261017180300_ep_met_goal_lifecycle_commands.sql:89–110
- **Qué está mal:** `reassign_goal_reserve_v1` puede convertir la meta origen de `COMPLETED` a `ACTIVE` sin comprobar el cupo ni tomar su bloqueo. CREATE, UPDATE y RELEASE sí tienen controles para impedir esa transición por encima del límite. No hay una definición posterior que sustituya esta función.
- **Escenario de fallo:** usuario Free con dos metas activas B/C y una A completada con reserva 100 y objetivo 100; reasigna 10 de A a B, cuyo objetivo es 1000 → A queda activa con 90, B y C siguen activas: tres metas activas. Incluso sin concurrencia se viola el máximo de FR-005 de EP-MET.
- **Fix mínimo:** comprobar esta reactivación bajo el mismo bloqueo de cupo y rechazarla sin efectos si no existe una resolución explícita del exceso. Test mínimo: el caso A/B/C anterior debe conservar estados y reservas al rechazar.

#### [SQL3-03] Un CREATE de recurrencia aplicado puede rechazarse al reintentarlo
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261018190500_ep_rec_create_update.sql:202–213
- **Qué está mal:** `create_recurrence_v1` valida el estado actual de las referencias antes de consultar el recibo de la operación. El recibo debe permitir recuperar un resultado ya aplicado aunque después cambie la cuenta o categoría.
- **Escenario de fallo:** dispositivo A crea la regla y pierde la respuesta; B archiva su cuenta; A reenvía exactamente la misma operación y hash → recibe `REFERENCE_NOT_FOUND` en lugar del resultado guardado, aunque la recurrencia ya existe. El cliente recibe un rechazo de una creación que sí quedó persistida.
- **Fix mínimo:** validar identidad, estructura y hash, consultar el recibo y devolverlo antes de validar referencias para una creación nueva. Test mínimo: crear, archivar la cuenta y repetir el mismo comando; debe devolver el recibo original sin otra fila ni evento de sync.

#### [SQL4-02] El pago de tarjeta calcula fondos ignorando movimientos revisados
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261031163043_credit_purchase_terms_and_cycles.sql:1007–1015
- **Qué está mal:** `allocate_credit_payment_v1_pre_liability` suma solo asientos cuya transacción sigue `CONFIRMED`. La edición canónica cambia el estado a `REVISED` y agrega compensaciones: al excluir toda esa transacción se pierde su efecto vigente. El wrapper público final de SQL2 llama a esta implementación antes de publicar el pasivo y no recalcula los fondos.
- **Escenario de fallo:** Una cuenta tiene S/100 iniciales y un gasto de S/90 editado a S/95. El saldo real es S/5, pero este cálculo descarta los tres asientos del gasto revisado y obtiene S/100: acepta pagar S/20 a la tarjeta y deja la cuenta sobregirada; un ingreso revisado produce el error contrario y rechaza pagos válidos.
- **Fix mínimo:** Reutilizar el cálculo canónico que suma asientos originales, compensaciones y reemplazos una sola vez, incluyendo los hechos de apertura con el contrato actual. Test mínimo: gasto revisado a S/95 desde cuenta con S/100 → pago de S/20 rechazado sin nuevos asientos.

#### [SQL4-03] Reactivar instrumentos aplica un cupo distinto al de crearlos
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261101140000_credit_card_principal_quota_archive.sql:304–313
- **Qué está mal:** La reactivación cuenta filas de cuentas y tarjetas, incluye pasivos y omite tanto `private.instrument_quota_groups` como `private.is_user_pro`. La corrección de cupo compartido sí se usa en creación y registro, pero no aquí.
- **Escenario de fallo:** Un usuario Premium con cuatro instrumentos activos intenta reactivar una cuenta archivada y recibe `FREE_LIMIT_REACHED`. Un usuario Free con cuatro bancos tampoco puede reactivar la tarjeta de débito de uno de esos bancos, aunque comparte un cupo que ya estaba ocupado.
- **Fix mínimo:** Aplicar el guard compartido al número de grupos que quedará después de reactivar, bajo el mismo bloqueo de cupo por propietario usado por creación. Probar una reactivación Premium sobre cuatro instrumentos y una tarjeta de débito Free que comparte grupo.

#### [SQL4-04] Un reintento antiguo de archivo sobrescribe una reactivación posterior
- **Severidad:** alta · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261101140000_credit_card_principal_quota_archive.sql:299–344
- **Qué está mal:** El RPC extrae `operation_id` pero nunca lo consulta ni lo guarda; tampoco compara una revisión esperada. Cada reenvío vuelve a actualizar el estado e incrementar la revisión, incluso después de otra acción confirmada.
- **Escenario de fallo:** El dispositivo A archiva una cuenta con operación X y pierde la respuesta. B la reactiva y sincroniza; al reconectarse A reenvía X y el servidor vuelve a archivarla, haciendo desaparecer de los selectores una cuenta que B ya había reactivado.
- **Fix mínimo:** Guardar y consultar un recibo por propietario/operación antes de mutar, con hash del comando y bloqueo de idempotencia, como los demás RPC del módulo. Test mínimo: archivo X → reactivación Y → reenvío X devuelve su recibo y mantiene la cuenta activa.

#### [SQL4-05] El interruptor de planes depende de un usuario ficticio ausente
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261021125728_plan_qa_entitlement_overrides.sql:176–182; supabase/migrations/20261103152406_delete_account.sql:40–47; supabase/migrations/20261103162347_account_deletion_concurrency_fix.sql:6–13; supabase/tests/database/dev_plan_mode_test.sql:3–5
- **Qué está mal:** `set_test_plans_enabled` registra el cambio global con `user_id=00000000-0000-0000-0000-000000000000`. El guard de borrado se instala también sobre esa auditoría y exige que cada `user_id` exista en Auth. Ninguna migración crea ese usuario; los tests lo inventan expresamente antes de usar el interruptor, ocultando la incompatibilidad.
- **Escenario de fallo:** Tras aplicar las migraciones a una base sin ese usuario artificial, el operador ejecuta `internal.set_dev_plan_mode(false, 'preparación del lanzamiento')`. El INSERT de auditoría lanza `42501` y revierte también el UPDATE del interruptor; si estaba habilitado, sigue habilitado.
- **Fix mínimo:** Separar la auditoría global de la propiedad de usuarios: permitir sujeto NULL para eventos del interruptor y escribir NULL en esos eventos. El test debe cambiar el interruptor en ambos sentidos sin crear un usuario centinela y comprobar el estado persistido.

#### [SQL1-07] Dos primeras peticiones del mismo bucket provocan un rechazo falso
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20260920170000_create_auth_rate_buckets.sql:36–41,78–81
- **Qué está mal:** `FOR UPDATE` no bloquea una fila inexistente. Si dos llamadas leen el mismo bucket nuevo, ambas van al `INSERT` sin manejo de colisión; este helper sigue vigente y también lo usa el wrapper de cuatro argumentos restaurado posteriormente.
- **Escenario de fallo:** dos inicios de sesión simultáneos desde un origen cuyo bucket aún no existe leen «sin fila»; una inserción gana y la otra falla con `23505`. `auth-access` convierte ese fallo del limitador en una denegación de 60 segundos, aunque solo se consumió un intento y el máximo normal es cinco.
- **Fix mínimo:** crear primero el bucket con `INSERT ... ON CONFLICT DO NOTHING` y después bloquearlo e incrementar el contador, preservando que la primera llamada cuente como un intento. Test mínimo: dos sesiones con la misma clave nueva devuelven resultados válidos y dejan contador 2.

#### [SQL1-08] La deduplicación de selección de plan ocurre antes del bloqueo
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20260915000000_create_plan_selection.sql:67–77
- **Qué está mal:** `apply_plan_selection` lee el recibo antes de tomar el bloqueo de la cabecera y no vuelve a leerlo después de esperar. La cabecera serializa las revisiones, pero el valor `known` sigue siendo el que se leyó antes del bloqueo.
- **Escenario de fallo:** dos solicitudes concurrentes llevan el mismo propietario, operación y revisión; ambas leen que no hay recibo. La primera confirma; la segunda obtiene después el bloqueo, calcula `CONFLICT` con su copia vacía de `known` e intenta insertar otra vez el mismo recibo, fallando con `23505` en vez de devolver `DUPLICATE`. Los tests existentes solo reenvían en secuencia.
- **Fix mínimo:** mover la lectura del recibo después del `SELECT ... FOR UPDATE` de la cabecera, como ya hace el flujo de selección de cupos. Test mínimo: dos sesiones enviando exactamente la misma operación deben producir `APPLIED`/`DUPLICATE` y un solo recibo.

#### [SQL2-03] Un reintento de selección deja de ser idempotente al archivar un recurso
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** `supabase/migrations/20261007144500_category_free_quota_5_plus_5.sql:409–431,448–454`.
- **Qué está mal:** `apply_plan_quota_selection` comprueba la elegibilidad actual de los recursos antes de consultar el recibo de la operación ya aceptada. No hay una definición posterior de este RPC que cambie ese orden.
- **Escenario de fallo:** A envía una selección de cuentas, el servidor la acepta y se pierde la respuesta; B archiva una de esas cuentas. Al reintentar exactamente la operación aceptada, A recibe `Selection contains an ineligible resource` en lugar de `DUPLICATE`. El mismo problema aparece con una categoría desactivada o una deuda cerrada entre aceptación y reintento.
- **Fix mínimo:** normalizar y calcular la identidad de la petición, bloquear y consultar su recibo antes de validar la elegibilidad mutable; validar recursos solo para una operación nueva. Añadir el caso mínimo aceptar selección → archivar cuenta → reenviar misma operación, comprobando `DUPLICATE` y ausencia de mutaciones nuevas.

#### [SQL2-04] Un verificador vencido puede liberar el trabajo de su reemplazo
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** `supabase/migrations/20260927003156_billing_purchase_verification_s3.sql:316–353`.
- **Qué está mal:** el permiso temporal de acknowledgement se recupera tras cinco minutos, pero `complete_billing_purchase_acknowledgement` y `release_billing_purchase_acknowledgement` reciben únicamente el hash de la compra. No identifican qué adquisición están completando o liberando. Estas tres funciones permanecen vigentes.
- **Escenario de fallo:** A adquiere el trabajo y queda demorado; cinco minutos después B lo recupera. A recibe finalmente un error y ejecuta `release`, que cambia a `PENDING` el trabajo que ahora pertenece a B; C puede adquirirlo mientras B sigue llamando al proveedor. Los llamadores efectivamente liberan por hash al fallar (`supabase/functions/verify-purchase/index.ts:263–270,288–302`). No se afirma un cobro duplicado: falla la exclusión entre verificadores y el estado deja de representar al trabajador activo.
- **Fix mínimo:** devolver un identificador de adquisición, exigirlo al completar/liberar y actualizar solo si coincide con el actual; adaptar esos dos llamadores. El test mínimo debe adquirir A, vencer su plazo, adquirir B y verificar que liberar A no modifica B; las llamadas consecutivas del test actual (`supabase/tests/database/billing_purchase_lifecycle_test.sql:53–56`) no cubren recuperación de leases.

#### [SQL2-05] El backfill de compras legacy no supera la validación posterior
- **Severidad:** media · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** `supabase/migrations/20260925022130_s3_credit_canonical_purchase_payment.sql:1154–1164`; `supabase/migrations/20260930034213_sprint_3_validate_integrity_constraints.sql:11`.
- **Qué está mal:** el importador inserta compras `EXPENSE/CARD_PURCHASE` sin `category_id`. La migración de proyección añade la obligación de categoría como `NOT VALID` (`20260928110000_credit_card_pull_projection.sql:296–307`), pero la migración del 30 de septiembre la valida sin reparar esas filas. No hay backfill intermedio que les asigne una clasificación; el cambio posterior del CHECK en SQL3 sigue exigiendo categoría para `CARD_PURCHASE` y se ejecuta después de este punto de fallo.
- **Escenario de fallo:** una instalación anterior a S3 contiene una compra `CREDIT_PURCHASE/POSTED` válida. El backfill la importa y la actualización llega al `VALIDATE CONSTRAINT`, que falla por esa misma compra con categoría nula; las migraciones posteriores no pueden aplicarse. Es un problema de actualización con datos legacy, no del arranque de una base vacía; la documentación indica que los datos actuales de prueba se eliminarán antes de producción.
- **Fix mínimo:** establecer y ejecutar una reparación explícita de categorías legacy antes de validar, conservando la clasificación de origen cuando exista y requiriendo decisión de producto si falta. Añadir un test de actualización con una compra legacy previa a S3 hasta el paso de validación: el test asignado de backfill crea su compra por el RPC nuevo y con categoría (`supabase/tests/database/credit_sprint3_legacy_backfill.test.sql:28–34`), por lo que no prueba este camino.

#### [SQL3-04] Anular principal e interés desde dos dispositivos causa un fallo definitivo evitable
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261008150000_s5_settle_debt.sql:351–380,524–525
- **Qué está mal:** cada anulación bloquea primero el movimiento seleccionado, después el principal y después el interés. Seleccionar distintos miembros del mismo pago invierte el orden de bloqueo. Además, el manejador captura el bloqueo mutuo y lo devuelve como `SETTLEMENT_VOID_FAILED` con `retryable=false`.
- **Escenario de fallo:** A anula el principal y mantiene su bloqueo; B anula el interés y mantiene el suyo; A espera el interés y B espera el principal → PostgreSQL aborta una operación por bloqueo mutuo y esa operación se presenta como fallo no reintentable. El test actual anula solo desde el interés en una sesión (`debt_settlement_test.sql:176–196`).
- **Fix mínimo:** descubrir el grupo sin bloquear un miembro arbitrario y bloquear el grupo siempre en el mismo orden antes de revalidar revisiones. Test mínimo: dos sesiones anulando simultáneamente principal e interés, sin error definitivo y con una sola compensación financiera.

#### [SQL3-05] Se puede pagar una cuota de un cronograma cancelado
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261008150000_s5_settle_debt.sql:166–184,250–255
- **Qué está mal:** al seleccionar `installment_id`, el RPC comprueba dueño y borrado pero no que la cuota siga pendiente o parcial. Después sobrescribe su estado, incluso si era `CANCELLED`. El reemplazo de cronograma conserva precisamente esas cuotas como historial cancelado (`20261008160000_s5_debt_schedule.sql:180–182`).
- **Escenario de fallo:** se reemplaza un cronograma y la cuota antigua I queda cancelada; llega un comando con la revisión vigente de la deuda pero todavía referido a I y un principal menor que su importe pendiente → se aplica el pago y I vuelve a `PARTIAL` o `PAID`, mientras el cronograma nuevo sigue intacto. Se contabiliza el pago contra un plan que ya no está vigente.
- **Fix mínimo:** rechazar cuotas cuyo estado no sea `PENDING` o `PARTIAL` antes de insertar movimientos. Test mínimo: cancelar/reemplazar el cronograma e intentar pagar una cuota anterior; no deben aparecer movimientos ni eventos.

#### [SQL3-06] Alias y preferencias también pierden la respuesta idempotente al cambiar sus referencias
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261008161540_ep_cco_s5_alias_commands.sql:39–57; supabase/migrations/20261008161631_ep_cco_s5_preference_commands.sql:28–66
- **Qué está mal:** ambos upserts validan comercio/categoría vigentes antes de consultar el recibo. Un cambio posterior de esas referencias transforma una repetición válida en un error nuevo.
- **Escenario de fallo:** una preferencia se aplica, se pierde su respuesta y después se inactiva la categoría; repetir el mismo comando devuelve `CATEGORY_NOT_ACTIVE_OR_OWNED` en vez de `DUPLICATE`. Para un alias, inactivar el comercio provoca `MERCHANT_NOT_ACTIVE` en la misma situación.
- **Fix mínimo:** recuperar primero el recibo bajo el bloqueo de operación y comprobar las referencias solo si la operación es nueva. Test mínimo: repetir una preferencia ya aplicada después de inactivar su categoría y exigir el recibo original.

#### [SQL3-07] La plantilla recurrente admite categorías incompatibles con el gasto
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261018190500_ep_rec_create_update.sql:134–136
- **Qué está mal:** `_rec_template_references_valid` acepta cualquier categoría propia activa para `EXPENSE`, sin comprobar `category_type`. La escritura de movimientos sí rechaza categorías de ingresos para gastos; la validación final está en `supabase/migrations/20261104051901_system_category_expense_catalog.sql:41–47`.
- **Escenario de fallo:** se crea una recurrencia de gasto con una categoría propia `INCOME` → CREATE responde `APPLIED`, pero su plantilla no puede registrarse como gasto con esa misma categoría. La regla queda aceptada con referencias que contradicen el contrato de movimientos que debe cumplir.
- **Fix mínimo:** exigir categoría `GENERAL` o `EXPENSE` en esta rama. Test mínimo: CREATE de gasto con categoría de ingresos debe rechazarse sin recurrencia, recibo ni fila de sync.

#### [SQL3-08] Cambiar el intervalo CUSTOM evita la validación de cambio de cadencia
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261018190500_ep_rec_create_update.sql:266–268
- **Qué está mal:** exigir los cuatro campos de calendario depende solo de que cambie `frequency`. También cambia la cadencia al modificar `interval_count` o `custom_interval_unit` manteniendo `frequency='CUSTOM'`. El contrato aprobado exige los cuatro campos para cualquier cambio de cadencia (`specs/007-ep-rec-recurrencias-obligaciones/contracts/recurrence-command-contract.md:84,91`).
- **Escenario de fallo:** regla CUSTOM semanal con intervalo 1, inicio 2026-01-01 y próxima fecha 2026-01-15; se envía únicamente intervalo 2 y `reminder_lead_days` vigente → el RPC cambia la cadencia y responde `APPLIED` sin que el comando incluya frecuencia, unidad ni próxima fecha. La fecha heredada pertenece a ambas secuencias y la validación posterior no detecta la omisión.
- **Fix mínimo:** comparar los tres componentes de cadencia antes de exigir el conjunto completo. Test mínimo: ese parche parcial debe devolver `CADENCE_FIELDS_REQUIRED` y conservar la regla.

#### [SQL3-09] Números enteros escritos con decimales fallan después de validarse
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261018190500_ep_rec_create_update.sql:168,198,216,239–249
- **Qué está mal:** el validador acepta números JSON matemáticamente enteros, pero después algunos campos se convierten de texto directamente a `bigint`/`smallint`. PostgreSQL no acepta `1.0` como entrada textual de `bigint`. El contrato sí acepta esa grafía; el test numérico actual verifica el helper, no un RPC con esos campos (`recurrence_create_update_test.sql:24–52`).
- **Escenario de fallo:** un CREATE correcto con `interval_count: 1.0` y hash canónico correcto supera `_rec_valid_integer`, pero el cast de calendario lanza una excepción → devuelve `INVALID_COMMAND`. Un UPDATE con `expected_revision: 1.0` falla por la misma razón aunque sea equivalente a la revisión 1.
- **Fix mínimo:** tras validar integralidad y rango, convertir mediante `numeric` al entero requerido. Test mínimo: el mismo CREATE con intervalo `1` y `1.0` debe tener el mismo resultado semántico; comprobar también revisión decimal en UPDATE.

#### [SQL3-10] El harness de migración puede imprimir PASS con aserciones pgTAP fallidas
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/tests/database/debt_sprint5_migration_upgrade.ps1:45–49
- **Qué está mal:** se ejecuta pgTAP mediante `psql` y se usa únicamente su código de salida. Una aserción pgTAP fallida produce una fila TAP `not ok`, sin ser un error SQL; `ON_ERROR_STOP` no convierte ese resultado en fallo del proceso.
- **Escenario de fallo:** la comprobación de saldo esperado 8250 en `debt_sprint5_migration_test.sql:71–72` devuelve `not ok` por una regresión, pero ninguna consulta lanza excepción → `psql` termina con 0 y el script imprime que la actualización preservó los datos.
- **Fix mínimo:** ejecutar el archivo con un runner TAP que devuelva error cuando falle una aserción, o comprobar explícitamente sus resultados antes del mensaje PASS. Test mínimo del harness: introducir un `SELECT fail(...)` en una entrada sintética y exigir salida no cero.

#### [SQL3-11] El test de concurrencia de deudas falla durante el setup actual
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/tests/database/debt_sprint5_quota_concurrency.ps1:16–17
- **Qué está mal:** el fixture inserta una cuenta `SAVINGS` sin `preset_id`. En el esquema final, `accounts_validate_preset_compatibility` exige un preset bancario no nulo para ese tipo (`20261012100000_enforce_account_preset_compatibility.sql:14–16` y trigger en `20261012100100_enable_account_preset_compatibility.sql:6–8`).
- **Escenario de fallo:** ejecutar el harness sobre todas las migraciones de `main` → el INSERT de setup lanza `INVALID_REQUEST: Savings and bank accounts require a bank preset`; nunca se lanzan las dos aperturas que deberían probar el último cupo Free.
- **Fix mínimo:** añadir `preset_id='GENERIC'` al fixture, como ya hacen los tests SQL de deudas. Comprobar que el harness alcanza y verifica ambos resultados concurrentes.

#### [SQL3-12] Los harnesses de metas no completan su ejecución y limpieza con el esquema final
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/tests/database/goal_release_quota_concurrency.py:50–63,228–236; supabase/tests/database/goal_reserve_concurrency.ps1:96–105
- **Qué está mal:** el harness Python crea `pg_temp.goal_cmd` como postgres y luego lo llama como `authenticated` sin conceder EXECUTE, que ya no se hereda de PUBLIC tras el cambio de privilegios por defecto. Además, ambos harnesses borran `financial_movements` antes de borrar sus asientos de apertura, cuya referencia usa `ON DELETE RESTRICT`.
- **Escenario de fallo:** en el esquema completo, el Python falla por permisos al invocar el firmante; su `finally` intenta borrar una apertura con asientos creados por el trigger y vuelve a fallar por la FK. El PowerShell alcanza la carrera, pero su limpieza falla por la misma FK y deja datos sintéticos persistidos.
- **Fix mínimo:** conceder EXECUTE al firmante temporal de Python, como hace `_goal_test_fixture.sql.inc:63–67`, y borrar primero los asientos de los usuarios sintéticos en ambas limpiezas. Verificar después que no quedan filas sintéticas, también al provocar un error durante la carrera.

#### [SQL3-13] La sobreutilización de tarjeta solo se comprueba buscando texto SQL
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/tests/database/allow_card_purchase_overutilization_p10.test.sql:11–24
- **Qué está mal:** el test busca la ausencia de una cadena de error y la presencia de una asignación en cuerpos de funciones. Nunca registra una compra que supere la línea ni comprueba el pasivo y los asientos resultantes. No encontré otro caso de sobreutilización en `supabase/tests`.
- **Escenario de fallo:** una regresión rechaza la compra mediante otro código de error o persiste un principal menor, manteniendo el texto buscado en otra rama → estas tres aserciones siguen aprobando aunque el flujo de dinero sea incorrecto.
- **Fix mínimo:** un caso funcional con línea 10000, deuda previa 9000 y compra 2000; exigir `APPLIED`, aumento íntegro del principal a 11000 y ningún asiento duplicado al repetir la misma operación. Sustituir las aserciones de texto por ese caso.

#### [SQL4-06] Cambiar el calendario recalcula mal los presupuestos históricos
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261024190000_ep_pre_budget_scope_snapshot.sql:182–196
- **Qué está mal:** `get_budget_status` calcula primero las fechas con el calendario actual del presupuesto y solo busca un snapshot que tenga exactamente esas fechas. Nunca elige primero el periodo histórico persistido que contiene la fecha consultada.
- **Escenario de fallo:** Junio tiene un snapshot mensual [01/06,01/07), límite S/300 y alcance Comida; en julio el presupuesto pasa a semanal y alcance Total. Consultar 10/06 calcula la semana [08/06,15/06), no encuentra el snapshot mensual y devuelve límite/alcance actuales y únicamente el gasto de esa semana.
- **Fix mínimo:** Para una fecha con periodo persistido, obtener primero ese periodo y usar sus fechas, límite y alcance; calcular límites desde el presupuesto solo cuando no exista snapshot. Test mínimo: snapshot mensual antiguo seguido de cambio a semanal y consulta dentro del mes antiguo.

#### [SQL4-07] Cada cuota o asignación recalcula todas las tarjetas del usuario
- **Severidad:** media · **Categoría:** escala · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261031163043_credit_purchase_terms_and_cycles.sql:146–171,1041–1057; supabase/migrations/20261101140000_credit_card_principal_quota_archive.sql:100–104
- **Qué está mal:** Los triggers por fila de cuotas y asignaciones llaman a un refresco de todos los vencimientos del usuario. Cada refresco vuelve a agregar pagos y reembolsos, y la distribución de reembolsos consulta de nuevo la CTE `base` por cada cuota, incluidas compras antiguas sin relación con el cambio.
- **Escenario de fallo:** Un usuario con miles de cuotas históricas paga una tarjeta y el pago se distribuye entre 36 cuotas. Las 36 inserciones de asignaciones disparan 36 recorridos completos y consultas correlacionadas sobre ese historial dentro de la misma transacción, manteniendo bloqueada la tarjeta y retrasando la sincronización del otro dispositivo.
- **Fix mínimo:** Acotar el refresco a la compra o tarjeta afectada y ejecutarlo una vez al terminar el comando de pago, conservando la atomicidad. Test mínimo de regresión: un pago distribuido mantiene los mismos estados y no actualiza cuotas de otra tarjeta; medir el plan local en una fase posterior autorizada.

#### [SQL4-08] Dos reintentos simultáneos de preferencias fallan con clave duplicada
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261103172646_profile_preferences_private_usage.sql:45–80,113–114
- **Qué está mal:** El RPC busca el recibo antes de bloquear el perfil. Después de esperar el bloqueo no vuelve a buscarlo, por lo que un segundo envío simultáneo intenta insertar el mismo recibo como conflicto en vez de devolver `DUPLICATE`.
- **Escenario de fallo:** Dos peticiones con igual operación y revisión 1 llegan juntas y ambas ven que no hay recibo. La primera aplica revisión 2; la segunda espera, lee revisión 2, entra en CONFLICT e inserta un recibo cuya clave ya existe: recibe `23505` en lugar del resultado idempotente esperado.
- **Fix mínimo:** Serializar propietario/operación antes de leer el recibo, o volver a consultarlo después de adquirir el bloqueo del perfil. Test mínimo: dos conexiones envían exactamente la misma operación a la vez; una aplica y la otra devuelve DUPLICATE sin excepción.

#### [SQL4-09] La versión de comercios hace un barrido global bajo bloqueo
- **Severidad:** media · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261102222216_merchant_discovery.sql:82–91
- **Qué está mal:** Cada INSERT/UPDATE de comercio obtiene un bloqueo global y calcula `MAX(version)` recorriendo la tabla, sin índice sobre `version` en las migraciones. Incluye comercios privados y las actualizaciones masivas de logos, por lo que el trabajo crece con el catálogo completo y se repite por cada fila modificada.
- **Escenario de fallo:** Miles de usuarios acumulan decenas de miles de comercios; la sincronización mensual actualiza miles de logos. Cada logo vuelve a recorrer el catálogo mientras conserva el mismo bloqueo hasta el commit, bloqueando también la creación de un comercio privado desde cualquier usuario.
- **Fix mínimo:** Añadir un índice sobre `merchant_services(version)` para que MAX lea el extremo del índice; conservar de momento la serialización y verificar después su coste con una carga representativa. Test mínimo: verificar con EXPLAIN, en una fase posterior autorizada, que MAX usa el índice y no un barrido completo.

### Deudas

Porciones: DEB1 (Codex gpt-6.1-sol medium); DEB2 (Claude sonnet medium (relevo)). Hallazgos: 28 (5 alta, 13 media, 10 baja).

#### [DEB1-01] Un worker de A puede enviar una deuda con la sesión de B
- **Severidad:** alta (el worker dijo crítica) · **Categoría:** seguridad · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/debts/data/remote/DebtApi.kt:29-38; app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtSyncScheduler.kt:81-95,104-110
- **Qué está mal:** El worker comprueba al propietario una vez al comenzar, pero `DebtApi.execute` obtiene la sesión vigente para cada comando sin recibir ni comparar el propietario de la outbox. `AuthenticatedSession` ya proporciona `userId`, pero se usa solamente su token. El cambio de sesión puede ocurrir mientras el worker espera una petición anterior; la cancelación de sesión consultada no cancela este worker de deudas.
- **Escenario de fallo:** A tiene dos aperturas históricas pendientes; el worker comienza como A y espera la primera petición. A cierra sesión y B inicia sesión antes de procesar la segunda: la segunda apertura se manda con el token de B. Al no referenciar cuenta y tomar la RPC al dueño de `auth.uid()`, se crea en B la deuda con nombre, notas y monto privados de A; el resultado también puede marcar como sincronizado el comando local de A. RLS no lo impide porque la petición efectivamente se autentica como B.
- **Fix mínimo:** Pasar `command.userId` como propietario esperado a `DebtApi.execute` y rechazar antes de enviar cuando `session.userId` no coincida; verificar también el propietario tras las suspensiones antes de aplicar resultados. Añadir un test que cambie A por B entre dos comandos y compruebe que la apertura de A nunca sale con el token de B.
- **Verificación (Fable):** Confirmado: `DebtApi.execute` usa la sesión vigente sin `expectedOwner` (cuentas sí lo comprueban). Baja a alta: exige cambiar de cuenta en el mismo teléfono durante una corrida del worker.

#### [DEB1-02] Ajustar o condonar deja un evento que bloquea su propio eco remoto
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/debts/data/local/DebtLocalDataSource.kt:592-605,832-841; app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtChangeFeedApplier.kt:199-206,260-269
- **Qué está mal:** El ajuste/condonación local guarda `occurredAt = clock.millis()`, pero no envía esa fecha en el comando. La RPC versionada genera la fecha al procesarlo (`supabase/migrations/20261008170000_s5_debt_closure.sql:28,131-133`). Ambos eventos tienen el mismo ID estable; el applier exige igualdad de `occurredAt` y rechaza el evento remoto como `DEBT_EVENT_ID_REUSED`.
- **Escenario de fallo:** Registrar una condonación offline el lunes y sincronizar el martes produce un evento local del lunes y otro remoto del martes con el mismo ID. La RPC se aplica, pero la página de cambios se revierte y el checkpoint no avanza. Los cambios posteriores de cualquier deuda de ese usuario quedan detrás de la página rechazada, incluso tras reiniciar.
- **Fix mínimo:** En el eco de un evento de cierre reconocido por su recibo/comando local, aceptar y actualizar la fecha que genera el servidor, conservando la comprobación de identidad, montos, delta y vínculos. Añadir un test local → respuesta RPC → página remota con fechas distintas; no relajar la igualdad para eventos arbitrarios.

#### [DEB1-03] El DTO no puede leer los conflictos reales del servidor
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/debts/data/remote/DebtDtos.kt:12-23; app/src/main/java/com/kipu/app/feature/debts/data/remote/DebtApi.kt:40-47; app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtSyncScheduler.kt:124-126,144-149
- **Qué está mal:** `DebtCommandRemoteResult.error` es `String?`, mientras las RPC de pago, cronograma y cierre devuelven un objeto `{"code":"DEBT_REVISION_CONFLICT"}`. La deserialización falla y `DebtApi` lo clasifica como fallo de red, por lo que no se ejecuta el tratamiento de `CONFLICT`. Contratos comprobados en `supabase/migrations/20261008150000_s5_settle_debt.sql:116-118`, `20261008160000_s5_debt_schedule.sql:87-89` y `20261008170000_s5_debt_closure.sql:88-90`.
- **Escenario de fallo:** Dos dispositivos pagan sobre la revisión 1; el primero crea la revisión 2. El segundo recibe HTTP 200 con `status=CONFLICT` y `error` objeto: el cliente lo convierte en `NetworkFailure`, conserva su pago optimista y reintenta indefinidamente el mismo comando obsoleto. Los comandos posteriores de esa deuda esperan al anterior.
- **Fix mínimo:** Representar `error` con un DTO que contenga `code` y leer `error?.code` al finalizar. Añadir a `DebtApiTest` una respuesta idéntica al conflicto SQL y verificar `Success` con estado `CONFLICT`, seguido de la transición de la outbox sin reintento de red.
- **Verificación (Fable):** Confirmado: `error` es `String?` y las RPC devuelven un objeto `{code}`; la deserialización cae en `NetworkFailure`.

#### [DEB1-04] El conflicto de una deuda detiene todas las demás
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtChangeFeedApplier.kt:48-76,103-138; app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtHydration.kt:26-30
- **Qué está mal:** Un conflicto lanza una excepción que revierte la página completa, deja el cursor anterior y marca la deuda como `CONFLICT`. En sincronización normal, `DebtHydration` devuelve éxito al recibir ese conflicto. No hay una ruta de resolución/rebase de outbox en esta porción; las siguientes lecturas encuentran el mismo conflicto y tampoco alcanzan cambios de otras deudas.
- **Escenario de fallo:** A y B editan el nombre de la misma deuda desde la revisión 1; A sincroniza primero. B ya tiene su nombre distinto en revisión 2 y recibe el snapshot remoto de revisión 2: `sameDebtContent` difiere y la página queda detenida. Una nueva deuda o un cobro independiente registrado en A, detrás de ese cambio, nunca aparece en B aunque su propia sincronización termine con éxito. Este caso usa la RPC de edición, cuyo conflicto no incluye el objeto de error de DEB1-03, por lo que es independiente de ese hallazgo.
- **Fix mínimo:** Aislar el cambio conflictivo y conservar su snapshot remoto pendiente de resolución antes de avanzar el cursor, permitiendo aplicar cambios de otras deudas sin sobrescribir la verdad financiera local. Añadir un test con deuda X en conflicto y una deuda Y nueva en la misma página, verificando que Y converge y X conserva el conflicto explícito.

#### [DEB2-01] El selector "Reducir / Aumentar" del ajuste no cambia el signo ya escrito
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** P/DebtScheduleScreen.kt:828, 1071-1102; P/DebtScheduleViewModel.kt:94, 169-174, 229-234
- **Qué está mal:** el signo vive en dos sitios. `isReduce` es un `remember` local de la pantalla; el signo real viaja dentro del texto `state.adjustmentAmount` (`"-$it"` solo cuando se escribe, línea 1102). Al pulsar el otro botón de la píldora (líneas 1075/1088) solo cambia `isReduce`; el texto del ViewModel conserva el signo antiguo y el campo lo oculta con `removePrefix("-")` (línea 1101).
- **Escenario de fallo:** deuda de S/ 100. El usuario deja "- Reducir" (por defecto), escribe 10 (el estado queda `-10`), cambia a "+ Aumentar" y pulsa "Aplicar ajuste de saldo". La UI muestra "Aumentar 10", pero `closeDebt(ADJUST)` parsea `-10` y el saldo baja a S/ 90. Es un movimiento de principal en dirección contraria a la elegida, y queda en el historial auditable.
- **Fix mínimo:** al cambiar el selector, reescribir el signo: `onAdjustmentAmountChange(if (reduce) "-$digits" else digits)` con `digits = state.adjustmentAmount.removePrefix("-").removePrefix("+")`. Mejor aún: no guardar el signo en el texto y pasar `isReduce` a `closeDebt`.
- **Verificación (Fable):** El signo se añade en `onValueChange`; cambiar el selector no reescribe el valor ya guardado.

#### [DEB1-05] Cada reconciliación puede volver a emitir un aviso ya entregado
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtReminderScheduler.kt:90-115,192-209; app/src/main/java/com/kipu/app/feature/debts/data/sync/DebtReminderWorker.kt:71-95,132-137
- **Qué está mal:** La reconciliación vuelve a encolar con `REPLACE` toda cuota pendiente. Cuando la fecha del aviso ya pasó, el retraso es cero; el worker no consulta si esa tupla usuario/cuota/fecha ya fue entregada y reemplaza su registro de programación antes de marcarlo entregado otra vez. El ID estable evita duplicar la fila del aviso local, pero no evita otro `notify` del sistema.
- **Escenario de fallo:** Una cuota vence el 20 y el aviso del día 18 ya se entregó y fue descartado. El día 19 se edita una nota de la deuda o acaba otra sincronización, se reconcilia y el aviso reaparece inmediatamente; posteriores reconciliaciones vuelven a emitirlo mientras siga pendiente.
- **Fix mínimo:** Consultar el registro existente de la misma tupla antes de reemplazarlo; si ya está entregado, no volver a publicar la notificación. Mantener el comportamiento actual de persistir el aviso local con permiso denegado. Test mínimo: ejecutar el recordatorio, reconciliar sin cambiar fecha y comprobar una sola publicación total.

#### [DEB1-06] El parser de deudas admite importes que Money y el servidor rechazan
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/debts/domain/DebtAmountParser.kt:10-18; app/src/main/java/com/kipu/app/feature/debts/data/local/DebtLocalDataSource.kt:76-78; app/src/main/java/com/kipu/app/feature/debts/domain/model/UpcomingDue.kt:77-83
- **Qué está mal:** Se valida únicamente que el importe positivo quepa en `Long`, sin el límite de `Money.MAX_MONEY_MINOR`. La apertura local tampoco aplica ese límite. El servidor admite hasta 99_999_999_999_999 centavos y la proyección de vencimientos construye `Money`, cuyo constructor lanza una excepción por encima de ese valor.
- **Escenario de fallo:** Introducir `1000000000000.00` en una deuda histórica con vencimiento produce 100_000_000_000_000 centavos: el parser y la escritura local lo aceptan. Al abrir vencimientos, `Money(remaining, currency)` falla; al sincronizar, la RPC rechaza la apertura que ya aparece guardada localmente. Es un caso de entrada extrema, no una corrupción demostrada con importes cotidianos.
- **Fix mínimo:** Aplicar el mismo límite compartido en el parser y en las fronteras de apertura/pago locales, también para intereses. Test mínimo: aceptar el máximo y rechazar máximo + 1 sin ninguna escritura; conservar la gramática decimal actual de deudas.

#### [DEB1-07] El planificador devuelve cuotas de principal cero como válidas
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/debts/domain/usecase/SetDebtSchedule.kt:24-26,35-45; app/src/main/java/com/kipu/app/feature/debts/data/local/DebtLocalDataSource.kt:485-491
- **Qué está mal:** El planificador permite más cuotas que centavos disponibles. Devuelve un plan `Valid` con cuotas de cero, aunque el repositorio y FR-014 exigen principal positivo para cada cuota.
- **Escenario de fallo:** Una deuda con 2 centavos restantes y 3 cuotas genera `[1,1,0]`, que la pantalla acepta como plan válido. Al guardar, el repositorio responde `INVALID_INSTALLMENT_SEQUENCE`, aunque la secuencia es correcta: falla el importe cero. Los tests del planificador solo cubren repartos con una base positiva.
- **Fix mínimo:** Rechazar al planificar cuando `installmentCount > remainingPrincipalMinor`, con un error específico de importe insuficiente para el número de cuotas. Test mínimo: saldo 2, cantidad 3 → `Invalid`, y cantidad 2 → dos cuotas de 1.

#### [DEB1-08] El test de revocación de permisos no provoca ninguna revocación
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/debts/data/DebtReminderWorkerTest.kt:50-65,146-149
- **Qué está mal:** `permissionRevokedBetweenChecksIsHandledAsOptionalNotification` pasa una secuencia `[true,false]`, pero comprueba que el permiso se consultó una sola vez. Solo se consume `true`; ni se simula una revocación del sistema ni se fuerza la `SecurityException` que captura el worker.
- **Escenario de fallo:** Si se elimina el `catch (SecurityException)` de `DebtReminderWorker.kt:132-141`, este test conserva el mismo camino y puede seguir pasando cuando el entorno permite notificar. Por tanto no detecta la regresión que su nombre afirma proteger: una revocación después de la comprobación haría fallar el worker.
- **Fix mínimo:** Sustituir este caso por uno que haga que la publicación de la notificación lance `SecurityException` después de devolver permiso concedido y verificar resultado `Success` más aviso local persistido. El test actual de permiso denegado ya cubre el otro camino; no duplicarlo.

#### [DEB2-02] La pestaña de cierre inventa datos cuando el saldo es cero
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** P/DebtScheduleScreen.kt:1188-1233 (en concreto 1210, 1215, 1227), 929-934, 1182
- **Qué está mal:** con `remainingPrincipalMinor == 0` la sección "2. Condonación" siempre muestra "S/ 0.00 exonerados", la fecha de apertura de la deuda como si fuera la fecha de condonación y el motivo `debt.notes ?: "Acuerdo mutuo de exoneración comercial"`. También pone "Completada con éxito" y "100% amortizado". Nada de eso sale de los eventos reales (hay `DebtSettlementActivity` con `FORGIVENESS`, que la pantalla ni recibe).
- **Escenario de fallo:** una deuda pagada íntegramente con pagos normales y sin ninguna condonación. Al abrir "Ajustes y cierre" el usuario lee una condonación completada con un "acuerdo mutuo de exoneración comercial" que nunca existió. Es un texto de auditoría falso en una pantalla que se vende como constancia contable.
- **Fix mínimo:** borrar el bloque `if (isZeroBalance) { Surface(...) }` de la sección 2 (líneas 1193-1233) y el subtítulo "Completada con éxito"; si se quiere mostrar la condonación real, pasar `closure` (ya existe `DebtClosureSummary`) en lugar de literales.
- **Ahorro estimado:** 40 líneas

#### [DEB2-03] La vista previa de cuotas no coincide con lo que se guarda
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** P/DebtScheduleScreen.kt:1546-1567 (1553), 640-642, 669-677, 608; domain/usecase/SetDebtSchedule.kt:35-45; P/DebtScheduleViewModel.kt:108
- **Qué está mal:** tres diferencias entre `calculateProjections` (solo UI) y `SetDebtSchedule.plan` (lo que se persiste): (a) la UI suma todo el resto de céntimos a la cuota 1 (`base + remainder`), el plan reparte 1 céntimo a cada una de las primeras `remainder` cuotas; (b) la UI numera siempre 1..n, el ViewModel numera desde `max(installmentNumber)+1` (línea 108); (c) la etiqueta dice "Mensual (cada 30 días)" pero ambos usan `plusMonths`.
- **Escenario de fallo:** saldo S/ 100.01 en 3 cuotas. Previsualiza 33.35 / 33.33 / 33.33; se guarda 33.34 / 33.34 / 33.33. Además, si había un cronograma cancelado con 3 cuotas, el usuario ve "Cuota 1, 2, 3" y después el detalle muestra "Cuota 4 de 3...". El texto "exactos" de la línea 724 afirma una suma que la UI no calcula con la misma regla.
- **Fix mínimo:** borrar `calculateProjections` y llamar a `SetDebtSchedule().plan(...)` desde la UI (o exponer la lista desde el ViewModel) para pintar la vista previa; cambiar la etiqueta a "Mensual".
- **Ahorro estimado:** 25 líneas

#### [DEB2-04] Editar una deuda pierde lo que el usuario está escribiendo cuando llega un cambio de Room
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** P/DebtEditViewModel.kt:48-66
- **Qué está mal:** `observeDebt` se recoge de forma continua y cada emisión hace `current.copy(counterpartyName = debt.counterpartyName, dueDate = ..., notes = ...)`. Cualquier cambio de la fila (sync que pasa `syncState` de PENDING a SYNCED, reconciliación de recordatorios, cambio desde otro dispositivo) pisa los campos editados.
- **Escenario de fallo:** el usuario abre "Editar deuda" mientras hay una sincronización en segundo plano, escribe una nota larga; termina el sync, Room emite y el texto vuelve al valor guardado. Sin aviso. (El `revision` también se actualiza en silencio, por lo que un guardado posterior pasa aunque el contenido remoto haya cambiado.)
- **Fix mínimo:** cargar una sola vez (`repository.observeDebt(...).first()` o `firstOrNull` dentro del `launch`) y no volver a copiar campos editables; solo refrescar `revision` si se quiere detectar conflicto.

#### [DEB2-05] Detalle, cronograma y hoja de pago ignoran el enmascarado de saldos
- **Severidad:** media · **Categoría:** seguridad · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** P/DebtDetailScreen.kt:344, 354, 358, 420, 483, 543, 550, 633-636; P/DebtScheduleScreen.kt:400, 511, 698, 724, 917, 1248-1263, 1282, 1302, 1480, 1525; P/DebtSettlementSheet.kt:89, 160, 167, 301, 325; P/PayableDebtFormScreen.kt:179
- **Qué está mal:** la app tiene un modo de ocultar importes (`LocalBalanceMasked`, provisto en `MainActivity.kt:310` y respetado por `MoneyText` y por la lista de deudas). Estas pantallas formatean con `NumberFormat.format(minor / 100.0)` o `debtMoney(...)`, que escriben el número sin consultar el local.
- **Escenario de fallo:** el usuario activa "ocultar saldos" para usar la app en público. La lista de deudas muestra "••••", pero al tocar una deuda el detalle, el cronograma y la hoja de "Registrar pago" muestran saldo pendiente, monto original, cuotas e historial en claro.
- **Fix mínimo:** sustituir esos `formatter.format(x / 100.0)` por `MoneyText`/`formatMoneyTextDisplay` (ya usados en DebtListScreen y DebtHistorySheet) o envolver `debtMoney` con `if (LocalBalanceMasked.current) "••••"`. Elimina de paso tres rutas de formateo duplicadas y el paso por `Double`.

#### [DEB2-06] El interés de un cobro (Me deben) se registra con una categoría elegida al azar
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** P/DebtSettlementViewModel.kt:90-99, 109-113, 168-169, 176-188; P/DebtSettlementSheet.kt:80, 240
- **Qué está mal:** el ViewModel autoselecciona la primera categoría válida (`categories.firstOrNull()`) y, si hay interés, la envía siempre (`categoryId = selected.takeIf { interest > 0L }`). La hoja solo muestra el selector cuando es `payable` (`needsInterestCategory = payable && ...`), así que en cobros el usuario no la ve ni la puede cambiar. `DebtLocalDataSource.kt:338,371` persiste ese `categoryId` en el movimiento de interés.
- **Escenario de fallo:** préstamo a un amigo, cobro con S/ 5 de interés. El ingreso queda categorizado como la primera categoría `INCOME/GENERAL` de la lista (por ejemplo "Sueldo"), que además puede variar entre dispositivos. Analytics y presupuestos suman ese ingreso donde no corresponde. Además las categorías sin presentación se llaman todas "Interés" (línea 96), indistinguibles.
- **Fix mínimo:** mostrar el selector también en cobros (quitar `payable &&` en la línea 80 y filtrar por `INCOME/GENERAL`), o enviar `null` en cobros si el dominio lo admite. Usar el nombre real de la categoría en lugar del literal "Interés".
- **Cross-módulo:** sí → DEB1 (decidir si el interés de cobros exige categoría)

#### [DEB2-07] Una cuota PARCIAL se muestra con el monto planificado, no con lo que falta
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** P/DebtDetailScreen.kt:317, 410-421, 483; P/DebtListScreen.kt:575-583; P/DebtListViewModel.kt:76-87; el propio comentario en P/DebtDueAlerts.kt:55
- **Qué está mal:** la lista y el detalle usan `DebtScheduleItem.principalMinor` (monto planificado) para "Próxima cuota" y "Cuota S/ X". Cuando el estado es `PARTIAL` (`DebtLocalDataSource.kt:412`), ya se pagó una parte. La pantalla de vencimientos sí usa el saldo restante (`DebtDueBalance.remainingMinor`).
- **Escenario de fallo:** cuota 1 de S/ 300; se paga S/ 100 de principal y pasa a PARTIAL. La lista y el detalle dicen "Cuota S/ 300.00 · vence en 3 días"; "Próximos vencimientos" muestra pendiente S/ 200.00 para la misma cuota. El usuario ve dos importes distintos para la misma obligación.
- **Fix mínimo:** en `DebtListViewModel`/`DebtDetailViewModel` mapear con el restante (`debtDao.getInstallmentRemaining` / `DebtDueBalance`) o, mínimo, no mostrar importe cuando `status == "PARTIAL"` y mostrar "Pago parcial".

#### [DEB2-08] Cancelar cronograma, cerrar deuda y eliminar no capturan excepciones ni protegen doble toque
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** P/DebtScheduleViewModel.kt:142-163 (`cancelSchedule`), 165-197 (`closeDebt`); P/DebtDetailViewModel.kt:158-175 (`delete`), 109-112
- **Qué está mal:** `saveSchedule` sí tiene `try/catch` y `AtomicBoolean`; sus tres hermanos no. Una excepción de Room o del programador de recordatorios dentro de `viewModelScope.launch` sin handler tumba la app. Sin guardia, dos toques en el mismo frame lanzan dos comandos con la misma `expectedRevision` (el segundo da "cambió en otro dispositivo" aunque el primero se aplicó). En `delete` además `errorMessage` nunca se limpia: el `copy` de la línea 109-112 la conserva para siempre.
- **Escenario de fallo:** sin espacio en disco o con la base bloqueada, el usuario pulsa "Archivar obligación" → `repository.closeDebt` lanza `SQLiteException` → crash en lugar de un mensaje. Segundo escenario: eliminar una deuda cuyo borrado es rechazado deja el aviso rojo "No se pudo completar la acción" en la pantalla hasta salir.
- **Fix mínimo:** reutilizar el patrón de `saveSchedule` (envolver en `try/catch (Exception)` re-lanzando `CancellationException`, y comprobar `isSaving` al entrar). En `delete`, limpiar `errorMessage` al empezar.

#### [DEB2-09] Reintentar tras un fallo posterior al commit local puede duplicar un pago o una deuda
- **Severidad:** media · **Categoría:** datos · **Confianza del worker:** baja · **Estado:** probable (confianza baja del worker, no reabierto)
- **Ubicación:** P/DebtSettlementViewModel.kt:190-195; P/DebtOpeningViewModel.kt:136-141; data/OfflineFirstDebtRepository.kt:67-78, 103-109
- **Qué está mal:** el repositorio hace `localDataSource.settleDebt(...)` (commit) y después `.also { syncScheduler.schedule; reminderReconciler.reconcile }`. Si cualquiera de estos lanza (WorkManager, `LocalDate.parse` en el reconciliador), la excepción llega al ViewModel, que la trata como "no se guardó" (`getOrElse` → "Revisa la conexión e inténtalo otra vez") y deja el formulario abierto. El siguiente toque genera otro `operationId`/`debtId`, así que la idempotencia no lo frena.
- **Escenario de fallo:** en una liquidación, el pago se guarda y el reconciliador lanza. El usuario ve error, vuelve a pulsar "Registrar pago" y registra el mismo pago dos veces (con la revisión nueva ya sincronizada en la pantalla), o crea la deuda duplicada.
- **Fix mínimo:** en el repositorio, aislar `schedule`/`reconcile` con `runCatching` para que un fallo del recordatorio no se propague después del commit. (Defecto del repo; aquí solo se nota.)
- **Cross-módulo:** sí → DEB1 (`OfflineFirstDebtRepository`)

#### [DEB2-10] Los ViewModels que mueven dinero no tienen test unitario
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** P/DebtSettlementViewModel.kt, P/DebtOpeningViewModel.kt, P/DebtEditViewModel.kt, P/DebtListViewModel.kt, P/DebtScheduleViewModel.kt:142-229
- **Qué está mal:** solo hay test de `DebtScheduleViewModel.saveSchedule`, `DebtDetailViewModel` (anulación) y `UpcomingDuesViewModel`. Los androidTest montan las pantallas con estado falso y lambdas escritas a mano, así que no ejecutan estos ViewModels. Los hallazgos 01, 06 y 08 no los atrapa nada.
- **Escenario de fallo:** cualquier cambio en `save()` de liquidación (por ejemplo, aceptar principal mayor al saldo o quitar la comprobación de moneda de cuenta) pasa CI sin que nada falle. - Fix mínimo (tests que faltan, un caso cada uno): (1) `DebtSettlementViewModel.save` con principal > restante no llama a `repository.settleDebt`; (2) `closeDebt(ADJUST)` con "-10" envía `principalDeltaMinor = -1000` y con "10" envía `+1000` (ligado a DEB2-01); (3) `DebtOpeningViewModel.save` en modo NEW_CASH_FLOW sin cuenta de la misma moneda no guarda; (4) `DebtEditViewModel` conserva el texto tras una segunda emisión del flow (ligado a DEB2-04).
- **Fix mínimo:** —

#### [DEB1-09] Dos planificadores de apertura solo existen para sus propios tests
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/debts/domain/usecase/OpenPayableDebt.kt:10-43; app/src/main/java/com/kipu/app/feature/debts/domain/usecase/OpenReceivableDebt.kt:10-44; app/src/main/java/com/kipu/app/feature/debts/domain/model/DebtCommandModels.kt:143-163
- **Qué está mal:** Ambos planificadores y sus dos modelos de resultado no tienen llamadores de producción. La apertura real valida y calcula de nuevo en `DebtLocalDataSource.openDebt`. La búsqueda completa, incluidos archivos ocultos, tests y referencias documentales, solo encontró definiciones, tests y menciones de tareas.
- **Escenario de fallo:** Cambiar en cualquiera de estos planificadores una validación de apertura o el signo del efectivo y actualizar su test no cambia ninguna apertura real de la app. Se mantiene una segunda representación de reglas financieras que los tests unitarios ejercitan sin proteger el código ejecutado.
- **Fix mínimo:** Borrar los dos planificadores y sus modelos sin uso; trasladar sus casos financieros útiles a las pruebas existentes del repositorio/datasource. Conservar el test de edición descriptiva en `OpenPayableDebtTest`, pues sí prueba un componente usado.
- **Ahorro estimado:** 109 líneas de producción; sin dependencias nuevas ni eliminación de verificaciones útiles.

#### [DEB2-11] Código muerto: constancia PDF de cierre
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** P/DebtDetailScreen.kt:129-151, 720-778, y los imports `android.graphics.*`, `rememberLauncherForActivityResult`, `Dispatchers`, `withContext`
- **Qué está mal:** `certificateLauncher` se crea pero nunca se lanza (`grep` en todo `app/src`: solo hay referencias dentro de este archivo, ninguna llamada a `.launch`), `certificateMessage` nunca se muestra, y `writeDebtClosureCertificate` solo la llama ese callback. Cada recomposición registra un `ActivityResultLauncher` inútil.
- **Escenario de fallo:** nadie puede generar la constancia; si se reactiva sin revisar, el PDF corta nombres largos (se dibujan sin salto de línea a 595 px) y usa `Double`.
- **Fix mínimo:** borrar ambos bloques y los imports huérfanos (o cablear un botón, si es una función pendiente de producto).
- **Ahorro estimado:** 90 líneas

#### [DEB2-12] Código muerto: alertas de vencimiento de deudas y sus tests
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** P/DebtDueAlerts.kt:9-69 (todo excepto `debtDueDayLabel`), test/.../DebtDueAlertsTest.kt (casi completo)
- **Qué está mal:** `buildDebtDueAlerts`, `buildDebtDueOverview`, `DebtDueAlert`, `DebtDueOverview` y `DEFAULT_DEBT_ALERT_LEAD_DAYS` solo se usan en el test (`grep` en `app/src/main`: solo la propia definición). El único símbolo vivo es `debtDueDayLabel` (usado en UpcomingDuesScreen.kt:306). `DebtListScreen` ya reemplazó las alertas por el botón "Ver vencimientos" (línea 171), y `UpcomingDues` calcula sus propias ventanas, por lo que esta lógica y sus 8 tests prueban un camino que ya no existe. Su regla de ventana (7 días por defecto) además difiere de la de `ObserveUpcomingDues`.
- **Escenario de fallo:** alguien arregla un bug en `buildDebtDueOverview`, CI queda en verde y el usuario no nota ningún cambio; o reutiliza la función creyendo que es la regla vigente.
- **Fix mínimo:** mover `debtDueDayLabel` junto a `UpcomingDuesScreen` y borrar el resto y los tests del overview (conservar el test de `debtDueDayLabel`).
- **Ahorro estimado:** 65 líneas de main y 80 de tests

#### [DEB2-13] `hasVerifiedPremium` copiado en dos ViewModels de deudas
- **Severidad:** baja · **Categoría:** fusionar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** P/DebtListViewModel.kt:114-119, P/DebtOpeningViewModel.kt:188-193 (existen otras dos copias idénticas en Goals)
- **Qué está mal:** cuatro copias idénticas de la verificación de plan Premium (UUID → cache de acceso → evaluador). Si cambia la regla de entitlement hay que acordarse de cuatro sitios; la lista y el alta ya deciden el límite Free con esta lógica.
- **Escenario de fallo:** se endurece la regla en una copia (por ejemplo, lease vencido) y la otra sigue dejando crear deudas, o al revés: la lista muestra el límite pero el alta no lo aplica.
- **Fix mínimo:** un único `suspend fun hasVerifiedPremium(...)` en un colaborador de `plans/data/entitlement` y llamarlo desde los cuatro.
- **Cross-módulo:** sí → GOA1 (las dos copias de goals) y UI2 (dueño del entitlement)
- **Ahorro estimado:** 12 líneas

#### [DEB2-14] Fechas con zonas horarias distintas según la pantalla
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** P/DebtDetailScreen.kt:369, 533, 613; P/DebtListScreen.kt:104; P/DebtHistorySheet.kt:59; P/DebtScheduleViewModel.kt:38; P/DebtFormUiState.kt:35; P/UpcomingDuesViewModel.kt:35, 99
- **Qué está mal:** `UpcomingDuesViewModel` y el cierre de deuda usan `America/Lima`; "hoy" en lista/detalle (`LocalDate.now()`), la fecha de cada evento (`ZoneId.systemDefault()`) y `openedOn`/primer vencimiento usan la zona del teléfono.
- **Escenario de fallo:** usuario peruano de viaje (o con el reloj en otra zona). A las 22:00 hora de Lima, "Vence hoy" en el detalle y "Vence mañana" en Próximos vencimientos; el mismo pago aparece con un día distinto en el historial (zona del teléfono) y en la fecha de cierre (Lima).
- **Fix mínimo:** una constante `LIMA_ZONE` compartida y usar `LocalDate.now(LIMA_ZONE)` / `atZone(LIMA_ZONE)` en estos sitios.

#### [DEB2-15] El motivo del cierre es un solo campo compartido por ajuste, condonación, liquidación y archivo
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** P/DebtScheduleScreen.kt:1118-1126, 1316-1321, 313; P/DebtScheduleViewModel.kt:96, 165-186
- **Qué está mal:** `closureReason` es un único `String` del estado. El texto escrito en "Motivo de condonación" aparece como "Motivo del ajuste" y viaja también con "Liquidar" y "Archivar".
- **Escenario de fallo:** el usuario escribe "Acuerdo con Carlos" para condonar, cambia de idea y aplica un ajuste de redondeo: el ajuste queda en el historial con el motivo "Acuerdo con Carlos".
- **Fix mínimo:** limpiar `closureReason` al abrir/cerrar cada sección o separar `adjustmentReason` y `forgivenessReason`.

#### [DEB2-16] "Reintentar" de Próximos vencimientos no hace nada tras un error de mapeo
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** P/UpcomingDuesViewModel.kt:68-102, 108
- **Qué está mal:** el `.catch { emit(UpcomingDuesUiState(...error...)) }` del `combine` externo termina el flujo. `retry()` solo incrementa `retries`, que alimenta el flujo interno ya cancelado. El error puede venir del propio mapeo (`Math.addExact`, `Currency.fromCode` con un código no soportado).
- **Escenario de fallo:** una fila con moneda no PEN/USD hace lanzar `Currency.fromCode` dentro del `combine`; la pantalla queda en "No se pudieron mostrar los vencimientos" y "Reintentar" no recarga ni reacciona a los filtros hasta salir de la pantalla y esperar 5 s.
- **Fix mínimo:** mover el `.catch` dentro del bloque que calcula cada estado (`runCatching` por emisión) para que el flujo siga vivo, o hacer que `retry()` reinicie el `stateIn`.

#### [DEB2-17] Acciones de cierre ofrecidas en deudas no activas y errores con código crudo
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** P/DebtScheduleScreen.kt:219-258, 1439-1463, 1509-1520, 1128-1139; P/DebtScheduleViewModel.kt:221-227
- **Qué está mal:** la pantalla de cronograma/cierre no mira `debt.status`. Una deuda `CANCELLED` (el detalle sigue mostrando el botón "Cuotas") ofrece ajustar, condonar y archivar; el repositorio las rechaza con `DEBT_NOT_ACTIVE` y `closureError` lo muestra como "No se pudo cerrar la deuda (DEBT_NOT_ACTIVE)".
- **Escenario de fallo:** usuario entra a una deuda cancelada con saldo pendiente, pulsa "Condonar" y recibe un mensaje técnico en lugar de ver las acciones deshabilitadas.
- **Fix mínimo:** `enabled = debt.status == ACTIVE && !state.isSaving` en los botones y un caso `"DEBT_NOT_ACTIVE"` en `closureError`.

#### [DEB2-18] Tests que no prueban lo que dicen o están en el sitio equivocado
- **Severidad:** baja · **Categoría:** test-faltante · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** androidTest/.../DebtMovementDetailTest.kt:20-46; androidTest/.../DebtEditDetailsFlowTest.kt:15-50; androidTest/.../ReceivableDebtFlowTest.kt:20-48
- **Qué está mal:** `DebtMovementDetailTest` prueba `MovementDetailSheet` y `VoidMovementDialog` (movimientos, no deudas). `DebtEditDetailsFlowTest` verifica las lambdas que el propio test define (`state.value = ...copy(...)`) y que el principal no es editable, pero no ejercita `DebtEditViewModel`. `ReceivableDebtFlowTest` solo comprueba que un texto y un botón deshabilitado aparecen, con callbacks vacíos.
- **Escenario de fallo:** se rompe el flujo guardar→`editDebtDetails` y estos tests siguen pasando; un cambio en el detalle de movimientos hace fallar un archivo de deudas.
- **Fix mínimo:** mover `DebtMovementDetailTest` a movements/presentation (MOV3); añadir el test unitario del ViewModel de edición descrito en DEB2-10 y borrar el androidTest redundante si no aporta más que la comprobación de "principal no editable".
- **Cross-módulo:** sí → MOV3 (reubicar el test de movimientos)

#### [DEB2-19] Rama vacía y ramas inalcanzables
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** P/DebtListScreen.kt:577-578; P/DebtOpeningViewModel.kt:153; P/DebtSettlementViewModel.kt:208-210; P/DebtScheduleViewModel.kt:193; P/DebtActionsSheet.kt:25 y P/DebtDetailScreen.kt:262
- **Qué está mal:** en `DebtStatusLine`, si no hay fecha y la cuota está PARTIAL o PAID se abre un `Column` vacío (no pinta nada). `DebtCommandResult.Retryable` no la produce ningún origen en `debts/data/local` (grep sin resultados), así que las cuatro ramas `Retryable` de los ViewModels son inalcanzables. `onDetail` de `DebtActionsSheet` se recibe y no se usa; en el detalle se pasa `{}`.
- **Escenario de fallo:** ninguno visible; es ruido que oculta el defecto DEB2-09, porque se asume que "Retryable" cubre los errores de guardado.
- **Fix mínimo:** borrar el `Column` vacío, el parámetro `onDetail` y las ramas `Retryable` hasta que exista un productor (el `when` seguirá exhaustivo porque la clase sellada lo exige: dejar una rama común).
- **Ahorro estimado:** 15 líneas

### Seguridad y auth

Porciones: SEC1 (Codex gpt-6.1-sol medium); SEC2 (Gemini 3.8 flash-high). Hallazgos: 27 (3 alta, 17 media, 7 baja).

#### [SEC1-01] Recientes conserva los saldos aunque después se bloquee la app
- **Severidad:** alta · **Categoría:** seguridad · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/MainActivity.kt:185-188,528-544; app/src/main/java/com/kipu/app/core/security/LocalLockCoordinator.kt:108-123
- **Qué está mal:** Al salir se registra una hora, pero se mantiene la pantalla financiera visible. No hay protección de la captura de ventana ni de la miniatura de recientes; el bloqueo se aplica al volver, después del minuto. Esto incumple la protección de vistas previas de FR-047.
- **Escenario de fallo:** Con desbloqueo local habilitado, abrir un saldo, pulsar Inicio y esperar dos minutos; otra persona abre recientes y ve la miniatura del saldo sin superar el desbloqueo de Kipu. El overlay que aparecerá al volver no borra esa captura anterior.
- **Fix mínimo:** Proteger la ventana frente a capturas cuando la protección local esté activa, incluyendo la miniatura de recientes; el cambio más pequeño es aplicar FLAG_SECURE durante ese modo. Añadir una comprobación de la política de ventana, además del test visual del overlay.
- **Verificación (Fable):** No hay `FLAG_SECURE` ni `setRecentsScreenshotEnabled` en todo `app/src/main`. Requiere decisión de producto (afecta capturas).

#### [SEC1-02] La combinación biométrica excluye Android 9 y 10
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/core/security/AndroidXBiometricGateway.kt:19-24,66-70
- **Qué está mal:** Se exige BIOMETRIC_STRONG | DEVICE_CREDENTIAL en todas las versiones. AndroidX Biometric 1.1.0 no admite esa combinación en API 28 y 29, aunque ambos sistemas están dentro del minSdk 24; la capacidad se presenta como no disponible y construir el prompt con ella puede lanzar IllegalArgumentException. El test del gateway solo usa API 34.
- **Escenario de fallo:** Un teléfono Android 10 con huella y PIN configurados abre el ajuste de protección: canAuthenticate para la combinación no da éxito y Kipu dice que no existe autenticación configurada. Si la preferencia ya está activa, intentar mostrar ese prompt usa una combinación rechazada.
- **Fix mínimo:** Elegir autenticadores compatibles con la versión del sistema y ofrecer la alternativa de credencial correspondiente; usar la misma elección al consultar capacidad y construir el prompt. Añadir un caso API 29 con huella/PIN y otro de credencial sin huella.
- **Verificación (Fable):** `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` no está soportado en API 28–29 y `minSdk = 24`.

#### [SEC2-03] Bloqueo indefinido de la aplicación detrás de `SessionEntryCurtainHost` sin mecanismo de timeout
- **Severidad:** alta · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/SessionEntryCurtain.kt:91-123
- **Qué está mal:** `SessionEntryCurtainHost` dibuja una cortina opaca sobre toda la pantalla consumiendo el botón atrás con `BackHandler(true) {}`. La cortina únicamente se levanta si `ready` es verdadero, lo cual exige estrictamente que la pantalla destino reporte estar lista (`homeReady = true`). Si el dashboard o la pantalla destino experimentan un error de red offline, fallo de consulta local Room o si `currentRoute` difiere por parámetros dinámicos, `markHomeReady()` nunca se invoca. No existe un tiempo límite de seguridad (timeout de escape).
- **Escenario de fallo:** Un usuario inicia sesión mientras la base local Room está ocupada o con datos corruptos/vacíos que dejan `dashboardLoaded == false` indefinidamente en `AccountsNavigation.kt:265`. La cortina "¡Hola de nuevo!" permanece en pantalla para siempre; el botón atrás está deshabilitado y el usuario no puede ni reintentar ni salir, viéndose forzado a forzar el cierre del proceso desde los ajustes de Android.
- **Fix mínimo:** En `SessionEntryCurtainHost`, agregar un `LaunchedEffect` con un retardo máximo de seguridad (p. ej., 6 a 8 segundos) que invoque `curtain.dismiss()` de manera forzosa si `ready` no se ha cumplido.
- **Cross-módulo:** sí → ACC3 (AccountsNavigation / DashboardScreen es quien marca `markHomeReady()`)
- **Verificación (Fable):** Sin timeout en la cortina; depende de que el dashboard siempre llame `markHomeReady`.

#### [SEC1-03] Recuperar la contraseña puede crear una clave que el login rechaza
- **Severidad:** media (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/domain/PasswordValidator.kt:16-18,22-35; app/src/main/java/com/kipu/app/feature/auth/domain/RecoveryUseCases.kt:60-71; supabase/functions/auth-access/index.ts:85-90,266-277
- **Qué está mal:** Kotlin acepta letras y dígitos Unicode; auth-access solo acepta A-Z/a-z y 0-9. El cambio de contraseña usa directamente updateUser, por lo que no atraviesa la validación ASCII del login.
- **Escenario de fallo:** Establecer «ááááááá1» desde un enlace válido: la validación Android lo acepta y envía el cambio a Auth. Si Auth acepta esa clave, el siguiente inicio con la misma clave obtiene 401 antes de comprobarla, porque no contiene una letra ASCII. La pantalla de registro también habilita contraseñas que el endpoint luego rechaza.
- **Fix mínimo:** Aplicar la misma política en Android, registro, login y restablecimiento; con la política actual del endpoint basta alinear las comprobaciones de letra/dígito en Kotlin. Test mínimo: la misma contraseña Unicode produce la misma decisión en ambos validadores y no queda aceptada solo para el reset.
- **Verificación (Fable):** Confirmado (`isLetter`/`isDigit` en Kotlin vs `[A-Za-z]`/`[0-9]` en la edge function). Baja a media: solo falla con contraseñas sin ninguna letra ASCII.

#### [SEC1-04] Un error al marcar pendientes impide retirar la sesión
- **Severidad:** media (el worker dijo alta) · **Categoría:** seguridad · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/data/SupabaseAuthRepository.kt:250-270; app/src/main/java/com/kipu/app/MainActivity.kt:532-540
- **Qué está mal:** markWaitingForAuth se ejecuta antes de revocar el propietario y borrar las credenciales. Si falla una escritura local, se devuelve failure sin hacer esa limpieza; el botón del bloqueo ignora el resultado y navega al login.
- **Escenario de fallo:** Con la app bloqueada y pendientes, SQLite falla al actualizar el outbox, por ejemplo por falta de espacio. Pulsar «Cerrar sesión» lleva al login, pero el propietario y el archivo de sesión siguen activos; volver a iniciar el proceso puede restaurar la sesión que el usuario creyó cerrar.
- **Fix mínimo:** Garantizar la revocación local y eliminación de credenciales aunque falle el marcado del outbox; conservar las filas con su propietario y reportar el fallo por separado. Test mínimo: hacer que markWaitingForAuth lance una excepción y comprobar NoOwner, borrado de credenciales y ausencia de restauración posterior.
- **Verificación (Fable):** Confirmado: si `markWaitingForAuth` lanza, no se borra la sesión. Baja a media: exige un fallo de Room.

#### [SEC1-05] El arranque publica acceso antes de conocer la protección local
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/core/session/SessionCoordinatorImpl.kt:28-35; app/src/main/java/com/kipu/app/core/security/LocalLockCoordinator.kt:44,53-79; app/src/main/java/com/kipu/app/MainActivity.kt:250-269,528-529
- **Qué está mal:** setActiveOwner publica Available inmediatamente. Otro collector consulta después Room para conocer la preferencia de bloqueo; mientras espera, lockState sigue DISABLED. Además, LocalLockCoordinator nunca llama a updateLockState: la barrera de sesión sigue Available aun cuando aparece el overlay. La restauración no usa la cortina de entrada del login interactivo.
- **Escenario de fallo:** Arrancar con sesión guardada y bloqueo activo, retrasando findSettings por una cola de trabajo de Room. La restauración puede abrir el dashboard mientras la consulta sigue pendiente y todavía no hay overlay. El test instrumentado espera al bloqueo y sustituye la pantalla por un if/else, mientras MainActivity compone el NavHost debajo del overlay, por lo que no detecta esta ventana.
- **Fix mínimo:** Mantener protegido el acceso mientras se carga la preferencia y publicar Available únicamente después de decidir si exige desbloqueo; conectar las transiciones de bloqueo con SessionCoordinator. Test mínimo: suspender findSettings y comprobar que ninguna pantalla privada ni Available se expone hasta resolverlo y, si está activo, autenticar.

#### [SEC1-06] Una interrupción de red consume el enlace de recuperación localmente
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/navigation/AuthDeepLinkHandler.kt:45-49,60-68; app/src/main/java/com/kipu/app/feature/auth/data/RecoverySessionInstaller.kt:25-42
- **Qué está mal:** Se añade el enlace al conjunto de consumidos antes de intercambiar o verificar su sesión. Si la red falla antes de que Auth lo reciba, el mismo enlace aún válido ya no puede reintentarse en el proceso actual.
- **Escenario de fallo:** Abrir por primera vez un enlace válido sin red: retrieveUser falla y ready queda false. Recuperar la conexión y volver a tocar el mismo correo produce InvalidOrConsumed; no vuelve a ejecutarse la instalación, aunque el proveedor nunca consumió el enlace.
- **Fix mínimo:** Registrar el consumo después de instalar correctamente, distinguiendo un intento en curso de uno completado; liberar el intento al fallar por transporte. Test mínimo: primer intento falla por IOException, segundo con el mismo enlace verifica e instala correctamente.

#### [SEC1-07] Cinco accesos correctos agotan la cuota de toda una IP
- **Severidad:** media · **Categoría:** escala · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/functions/auth-access/index.ts:131-139,280-290,472-483
- **Qué está mal:** Cada petición de login consume la cuota de IP antes de saber si es correcta y el adaptador fija cinco intentos por 900 segundos. Los accesos exitosos no liberan ese presupuesto. La política de intentos fallidos termina limitando también a usuarios independientes detrás de una IP compartida.
- **Escenario de fallo:** Seis usuarios de una oficina o una red móvil comparten IP y acceden con credenciales correctas dentro de quince minutos. Los cinco primeros consumen la cuota común y el sexto recibe 429 pese a no haber fallado nunca.
- **Fix mínimo:** Reservar el umbral estricto por identidad y establecer para la IP un límite que admita accesos legítimos compartidos, o contabilizar fallos para la espera progresiva. Test mínimo: seis logins correctos de identidades distintas detrás de una IP no disparan el umbral de fallos de una cuenta.

#### [SEC1-08] Tests de aislamiento y pendientes comprueban sus propios valores
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/feature/auth/domain/RecoveryAndSignOutTest.kt:13-21; app/src/test/java/com/kipu/app/feature/auth/AuthFeatureBoundaryTest.kt:41-53
- **Qué está mal:** El test de pendientes devuelve 3 + 2 desde un fake y comprueba 5, sin usar PendingChangesRepositoryImpl. El supuesto test de no convertir movimientos cambia una copia de UserProfile y comprueba que una variable local constante sigue valiendo 500. Ambos pasan aunque se rompa la integración que dicen proteger.
- **Escenario de fallo:** Quitar instrumentPending o customPending de count deja verde el test de pendientes, y el aviso de cierre puede omitir cambios reales. Modificar código real de persistencia de preferencias tampoco afecta al segundo test, porque no lo ejecuta.
- **Fix mínimo:** Sustituir el primero por una instancia real de PendingChangesRepositoryImpl con DAOs y un customSource controlados, comprobando la suma; retirar la afirmación de integración del segundo hasta probar el contrato real en el módulo correspondiente. El caso mínimo es un pendiente por cada fuente y propietario, con una segunda cuenta excluida.

#### [SEC1-09] El almacenamiento cifrado no tiene un test sobre su implementación
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/core/security/KeystoreEncryptedSessionStorage.kt:78-125,129-137; app/src/test/java/com/kipu/app/feature/auth/data/SupabaseAuthRepositoryTest.kt:26
- **Qué está mal:** Los tests de sesión sustituyen este almacenamiento por mocks; StoredAuthSessionTest solo prueba JSON y vencimiento. No comprueban escritura cifrada, lectura, rechazo de un archivo alterado ni borrado real, que forman la frontera de persistencia de credenciales.
- **Escenario de fallo:** Una regresión que guarde texto sin cifrar o que conserve una sesión después de delete puede pasar todos esos tests, aunque exponga credenciales o restaure una sesión cerrada.
- **Fix mínimo:** Añadir un test instrumentado del almacenamiento real: guardar una sesión, comprobar que el archivo no contiene el token en claro, recuperarla y verificar que delete impide recuperarla. Un segundo caso mínimo debe alterar el ciphertext y comprobar que no devuelve la sesión. No se propone ejecutarlo en esta auditoría.

#### [SEC2-01] Aserción rota en test instrumentado `AuthCelebrationScreensTest.kt` contra texto inexistente
- **Severidad:** media (el worker dijo alta) · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/auth/presentation/AuthCelebrationScreensTest.kt:34
- **Qué está mal:** El test instrumentado `accountCreatedShowsTheAddressAndContinuesOnce` afirma `rule.onNodeWithText("Siguiente paso: elegir tu plan").assertIsDisplayed()`. Sin embargo, `AccountCreatedScreen.kt:156` fue modificado para mostrar `"Tu dinero está listo"`. El string antiguo ya no existe en el árbol semántico del composable, provocando que este test falle de inmediato si se ejecuta contra un emulador o dispositivo real.
- **Escenario de fallo:** Al correr la suite de pruebas instrumentadas de autenticación, Compose UI busca el nodo con texto "Siguiente paso: elegir tu plan", falla por elemento ausente y lanza `AssertionError`, rompiendo la integración continua.
- **Fix mínimo:** Reemplazar `"Siguiente paso: elegir tu plan"` por `"Tu dinero está listo"` en `AuthCelebrationScreensTest.kt:34`. Test mínimo: verificar que el nodo `"Tu dinero está listo"` se muestre con visibilidad y estilo correctos.
- **Verificación (Fable):** El texto «Siguiente paso: elegir tu plan» no existe en `app/src/main`. Test roto.

#### [SEC2-04] Bloqueo permanente de `login()` y `register()` si la animación de `exitWithError` en `SessionEntryCurtainHost` se interrumpe
- **Severidad:** media (el worker dijo alta) · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/AuthViewModel.kt:76,133; app/src/main/java/com/kipu/app/feature/auth/presentation/SessionEntryCurtain.kt:61-75,126-130
- **Qué está mal:** En `AuthViewModel`, tanto `login()` como `register()` descartan cualquier ejecución si `entryCurtain.state.value?.errorExiting == true`. Cuando ocurre un error de login, se marca `errorExiting = true` y se delega la finalización (`finishErrorExit()`) a un `LaunchedEffect` en Compose que anima el alfa durante 180 ms. Si la actividad pasa a segundo plano, rota de pantalla o si el host de la cortina no está compuesto en ese instante, el efecto se cancela o no se ejecuta, `finishErrorExit()` nunca es invocado y `errorExiting` se queda en `true` permanentemente en el singleton `SessionEntryCurtain`.
- **Escenario de fallo:** Un usuario ingresa credenciales erróneas y presiona "Ingresar"; inmediatamente bloquea el teléfono o cambia a otra aplicación. La respuesta de error llega del backend en segundo plano y se invoca `exitWithError`. Al no completarse la animación en Compose, `entryCurtain.state.value.errorExiting` permanece en `true`. Al volver a abrir la app, los botones "Ingresar" y "Crear cuenta" quedan completamente muertos: pulsar cualquiera de ellos retorna de inmediato sin hacer nada.
- **Fix mínimo:** No supeditar el estado de negocio del ViewModel a un callback de animación de Compose; limpiar `errorExiting` de inmediato o con un timeout en `SessionEntryCurtain`, o restablecerlo en `AuthViewModel` ante cualquier cambio de entrada del usuario en `onEmailChanged`/`onPasswordChanged`.

#### [SEC2-05] Desincronización y congelamiento de cooldowns por retrasos basados en `delay()` en `AuthViewModel` y `RecoveryViewModel`
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/AuthViewModel.kt:45-48; app/src/main/java/com/kipu/app/feature/auth/presentation/RecoveryViewModel.kt:47-52
- **Qué está mal:** Ambas clases implementan la cuenta regresiva mediante bucles que hacen `delay(1_000)` y restan 1 segundo al estado. Si la app pasa a segundo plano, Android suspende la corrutina de `viewModelScope`. Cuando el usuario reabre la aplicación varios segundos o minutos después, el contador no ha avanzado según el tiempo real transcurrido y obliga al usuario a esperar el tiempo restante completo, a pesar de que el bloqueo del servidor ya expiró.
- **Escenario de fallo:** Un usuario falla sus intentos y entra en cooldown de 45 segundos. Inmediatamente minimiza la app durante 40 segundos para buscar su clave en un gestor de contraseñas y vuelve. El contador se reanuda en 44 segundos en lugar de mostrar los 5 segundos reales restantes.
- **Fix mínimo:** Calcular en cada iteración los segundos restantes comparando el reloj actual (`Instant.now()` o `System.currentTimeMillis()`) con la marca de tiempo de expiración absoluta (`blockedUntil` o `resendUntil`), en lugar de restar un acumulador ciego con `delay(1_000)`.

#### [SEC2-06] Saturación de TalkBack por live region en `AuthBanner` durante la cuenta regresiva de cooldown
- **Severidad:** media · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/AuthFormComponents.kt:374; app/src/main/java/com/kipu/app/feature/auth/presentation/AuthFormScreen.kt:168-169
- **Qué está mal:** `AuthBanner` incluye `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`. En `AuthFormScreen`, el banner muestra el mensaje de cooldown parametrizado con `uiState.cooldownSeconds`. Como este valor cambia cada 1.000 ms, la región activa encola una lectura de accesibilidad por cada segundo (hasta 60 seguidas), saturando la cola de voz de TalkBack e impidiendo al usuario escuchar el foco de otros controles.
- **Escenario de fallo:** Un usuario con discapacidad visual activa un cooldown de 30 segundos. TalkBack comienza a anunciar repetidamente cada segundo: "Demasiados intentos. Espera 29 segundos.", "Demasiados intentos. Espera 28 segundos.", etc., hablando por encima de cualquier otro elemento que el usuario intente tocar en el formulario.
- **Fix mínimo:** Quitar `liveRegion` del banner de cooldown o usar una región estática que no anuncie cada segundo transcurrido, permitiendo que el usuario lea el tiempo restante únicamente cuando enfoque el componente.

#### [SEC2-07] Borrado prematuro de `confirmation` en `AuthFormScreen` antes de comprobar validación en ViewModel
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/AuthFormScreen.kt:125
- **Qué está mal:** Al presionar "Crear cuenta", la función `submit()` ejecuta `confirmation = ""` antes de llamar a `onRegisterClick()`. Si la validación local del ViewModel falla (por ejemplo, el correo no tiene formato válido o la contraseña no cumple la longitud requerida en `PasswordValidator`), el correo o la contraseña muestran error, pero el campo de confirmación ya fue vaciado, forzando al usuario a volver a escribirlo.
- **Escenario de fallo:** El usuario ingresa `usuario@invalido`, contraseña `MiPassword123` y confirmación `MiPassword123`. Pulsa "Crear cuenta". La validación de email falla y muestra "Formato de correo no válido.", pero el campo "Confirmar contraseña" aparece en blanco y los chips marcan error, requiriendo reescribir toda la confirmación.
- **Fix mínimo:** No borrar `confirmation` de forma anticipada en `submit()`; borrarlo únicamente cuando el registro tenga éxito o cuando se cambie explícitamente de pestaña.

#### [SEC2-08] Ausencia de campo de confirmación de contraseña en `ResetPasswordScreen`
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/ResetPasswordScreen.kt:23-26; app/src/main/java/com/kipu/app/feature/auth/presentation/RecoveryUiState.kt:3-14
- **Qué está mal:** A diferencia de `AuthFormScreen` que exige confirmar la contraseña, `ResetPasswordScreen` solo presenta un único campo con máscara oculta (`AuthPasswordField`). Dado que el enlace de recuperación es de un solo uso y se consume al instante, si el usuario comete un error tipográfico en el teclado móvil al escribir su nueva clave, la contraseña errónea se guarda en el servidor sin posibilidad de verificación previa, dejando al usuario bloqueado sin saber qué clave quedó grabada.
- **Escenario de fallo:** Un usuario con un enlace de recuperación válido escribe su nueva clave con un carácter accidental (p. ej. `Contrasena1!` en vez de `Contrasena1`). Pulsa "Guardar contraseña". La clave errónea se actualiza con éxito y el token se consume. Cuando el usuario intenta iniciar sesión con `Contrasena1`, el acceso es denegado y debe solicitar otro enlace.
- **Fix mínimo:** Incorporar el campo `confirmPassword` en `RecoveryUiState` y en `ResetPasswordScreen`, validando que ambas coincidan con `PasswordValidator.validatePasswordConfirmation` antes de enviar la petición.

#### [SEC2-09] Posible caída por `SecurityException` no capturada al invocar app de correo en `RecoveryScreen`
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/RecoveryScreen.kt:87-92
- **Qué está mal:** Al presionar "Abrir aplicación de correo", se ejecuta `context.startActivity(...)` dentro de un bloque `try-catch` que únicamente captura `ActivityNotFoundException`. En perfiles de trabajo (Android Enterprise) o dispositivos con restricciones de visibilidad de paquetes (Android 11+), lanzar un `Intent.makeMainSelectorActivity` puede arrojar `SecurityException`, causando un crash no controlado del proceso.
- **Escenario de fallo:** Un dispositivo con perfil corporativo o políticas estrictas de aislamiento de intents pulsa "Abrir aplicación de correo" tras solicitar recuperación. El sistema lanza `SecurityException` y la aplicación se cierra inesperadamente.
- **Fix mínimo:** Capturar `Exception` de forma general o agregar `catch (_: SecurityException)` junto con `ActivityNotFoundException` para asignar el mensaje en `mailError`.

#### [SEC2-10] Asignación continua de `Path` y `PathMeasure` en cada frame de dibujo en animaciones
- **Severidad:** media · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/AuthCelebrationMotion.kt:259-260,368; app/src/main/java/com/kipu/app/feature/auth/presentation/WelcomeBackCurtain.kt:195-202; app/src/main/java/com/kipu/app/feature/auth/presentation/OnboardingIllustrations.kt:279-281
- **Qué está mal:** En `drawQuipuField`, `drawAccountSeal`, `LogoTile` y en `OnboardingPrivacyIllustration`, se instancian objetos `Path()` y `PathMeasure()` directamente en el cuerpo de dibujo de Compose en cada frame (60-120 Hz). Esto genera una constante recolección de basura (GC churn) durante transiciones complejas.
- **Escenario de fallo:** En dispositivos de gama de entrada o media (comunes en el mercado peruano), durante el iris de salida de `WelcomeBackCurtain` o el tejido de `AccountCreatedScreen`, el GC de Android se activa reiteradamente para limpiar centenares de `PathMeasure`, provocando micro-congelamientos y pérdida visible de fluidez (jank).
- **Fix mínimo:** Reutilizar o almacenar en caché las instancias de `Path` y `PathMeasure` fuera del lambda de dibujo (usando `remember` o `drawWithCache`).

#### [SEC2-11] `rememberSceneClock` detiene su avance a los 40 segundos congelando animaciones continuas
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/OnboardingMotion.kt:101-106; app/src/main/java/com/kipu/app/feature/auth/presentation/AuthFormScreen.kt:97; app/src/main/java/com/kipu/app/feature/auth/presentation/WelcomeBackCurtain.kt:81
- **Qué está mal:** `rememberSceneClock` anima el reloj hasta un límite fijo de 40 segundos (`time.animateTo(OnboardingTimeline.LOOP_END)`). Al ser reutilizado en `AuthFormScreen` para el balanceo y destello de los quipus del encabezado y en `WelcomeBackCurtain` para los puntos suspensivos ("WaitingDot"), al transcurrir 40 segundos la animación se congela por completo y nunca se reanuda.
- **Escenario de fallo:** Un usuario se toma más de 40 segundos en la pantalla de login leyendo los términos o verificando su contraseña. Al cumplirse 40 segundos, los hilos del quipu y los brillos del encabezado se paralizan abruptamente como si la app estuviera trabada.
- **Fix mínimo:** Para componentes continuos como `AuthHero` y `WelcomeBackCurtain`, desacoplar la animación de un reloj finito de 40 segundos y utilizar un ciclo continuo o `rememberInfiniteTransition()`.

#### [SEC2-12] `contentDescription = label` en campos de texto enmascara el contenido para TalkBack
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/AuthComponents.kt:115; app/src/main/java/com/kipu/app/feature/auth/presentation/AuthFormComponents.kt:305
- **Qué está mal:** Tanto `AuthTextField` como `AuthFloatingField` asignan `.semantics { contentDescription = label }` directamente sobre el composable `TextField`. En Jetpack Compose, aplicar `contentDescription` a un nodo editable sustituye la descripción semántica del texto ingresado, impidiendo que el lector de pantalla anuncie el valor real escrito por el usuario.
- **Escenario de fallo:** Un usuario invidente escribe su correo en el campo "Correo electrónico". Al volver a enfocar el campo para verificar lo que escribió, TalkBack lee únicamente "Correo electrónico" en lugar de leer el valor ingresado (`usuario@ejemplo.com`) ni la posición del cursor.
- **Fix mínimo:** Eliminar `.semantics { contentDescription = label }` de los `TextField`; Material 3 ya expone el label y el texto editable de forma accesible y estándar.

#### [SEC1-10] Permanecen dos tests de plantilla sin comportamiento de Kipu
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/ExampleUnitTest.kt:1-16; app/src/androidTest/java/com/kipu/app/ExampleInstrumentedTest.kt:1-24
- **Qué está mal:** Uno comprueba 2 + 2 y el otro compara el packageName con APPLICATION_ID. No protegen un flujo de negocio ni la seguridad de la app. Se buscaron referencias en todo el árbol: solo aparecen sus propias declaraciones.
- **Escenario de fallo:** Un APK que no autentica ni oculta saldos sigue satisfaciendo ambas comprobaciones; conservarlas añade resultados verdes sin detectar esos errores.
- **Fix mínimo:** Borrar ambos archivos; conservar los tests que sí invocan comportamiento real.
- **Ahorro estimado:** 40 líneas

#### [SEC2-02] Spinner invisible en `AuthPrimaryButton` por colisión de color con el fondo
- **Severidad:** baja (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/AuthComponents.kt:185-186
- **Qué está mal:** En `AuthPrimaryButton`, el contenedor del botón tiene color de fondo `colors.primaryDeep`. El indicador de progreso circular `CircularProgressIndicator` se dibuja con `color = colors.primaryDeep`. Al coincidir exactamente el color del spinner con el color del contenedor sobre el que se dibuja, el indicador de carga resulta invisible para el usuario.
- **Escenario de fallo:** En `RecoveryScreen` (al pulsar "Enviar enlace de recuperación"), en `ResetPasswordScreen` (al pulsar "Guardar contraseña") o en `OnboardingScreen` (al pulsar "Empezar" mientras persiste), el botón pasa a estado `loading = true` mostrando "Procesando…", pero el spinner circular a su izquierda no se percibe en pantalla al ser del mismo verde esmeralda que el botón.
- **Fix mínimo:** En `AuthComponents.kt:186`, cambiar `color = colors.primaryDeep` a `color = colors.onPrimaryDeep` (blanco o color contrastante del tema, tal como hace `AuthCtaButton` en `AuthFormComponents.kt:409`).
- **Verificación (Fable):** Spinner `primaryDeep` sobre `primaryDeep` al 25 %: bajo contraste, no invisible.

#### [SEC2-13] Cadenas de correo sin sanitizar (`trim`) enviadas a `AuthCredentials` y `SetupDraftStore`
- **Severidad:** baja · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/AuthViewModel.kt:87,145; app/src/main/java/com/kipu/app/feature/auth/presentation/RecoveryViewModel.kt:87
- **Qué está mal:** `PasswordValidator.validateEmail` ejecuta `email.trim()` internamente, por lo que valida correctamente correos con espacios al inicio o final. Sin embargo, en `login()`, `register()` y `submitRecoveryRequest()`, se envían las cadenas crudas `currentState.email` sin recortar hacia `AuthCredentials`, `setupDraft.begin()` y `savedStateHandle["submittedEmail"]`.
- **Escenario de fallo:** Un usuario que selecciona su correo mediante el teclado predictivo inserta un espacio al final (`"correo@ejemplo.com "`). La validación local pasa, pero las credenciales se transportan con el espacio superfluo.
- **Fix mínimo:** Aplicar `.trim()` o `PasswordValidator.normalizeEmail()` al construir `AuthCredentials` y registrar los drafts en `AuthViewModel` y `RecoveryViewModel`.

#### [SEC2-14] Color `0xFF0A5A54` hardcodeado en scrim de barra de estado en `AuthFormScreen` rompe tema oscuro
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/AuthFormScreen.kt:245
- **Qué está mal:** Al desplazarse la pantalla de login/registro, la barra de estado se oscurece mediante un scrim que tiene `Color(0xFF0A5A54)` hardcodeado. En modo oscuro, el fondo del encabezado utiliza tonos más profundos (`0xFF052E2A`), por lo que al hacer scroll aparece una barra verde claro discordante.
- **Escenario de fallo:** Con el modo oscuro del sistema activo, el usuario abre la pantalla de inicio de sesión y se desplaza hacia abajo para ver el botón de términos. La barra de estado superior cambia a un tono verde claro en vez de integrarse con la paleta oscura.
- **Fix mínimo:** Utilizar `if (colors.isDark) Color(0xFF052E2A) else Color(0xFF0A5A54)` o `colors.primaryDeep` en lugar del valor numérico fijo.

#### [SEC2-15] Constantes y funciones de extensión huérfanas en `AuthCelebrationMotion.kt` y `AccountCreatedScreen.kt`
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/AuthCelebrationMotion.kt:44,77; app/src/main/java/com/kipu/app/feature/auth/presentation/AccountCreatedScreen.kt:70-86
- **Qué está mal:** `AccountCreatedTimeline.REVEAL` y `WelcomeBackTimeline.REVEAL` (`const val REVEAL = 0.95f`) no se usan en ningún lugar del repositorio. Asimismo, la función de extensión `circleReveal` quedó huérfana dentro de `AccountCreatedScreen.kt` tras un refactor y únicamente es consumida por `WelcomeBackCurtain.kt`.
- **Escenario de fallo:** Mantenimiento confuso y código muerto que induce a error al sincronizar las líneas de tiempo de animación.
- **Fix mínimo:** Eliminar las constantes `REVEAL` y mover `circleReveal` a `WelcomeBackCurtain.kt` o `AuthCelebrationMotion.kt`.
- **Ahorro estimado:** 25 líneas.

#### [SEC2-16] Duplicación de wrappers `LoginScreen.kt` y `RegisterScreen.kt`
- **Severidad:** baja · **Categoría:** fusionar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/LoginScreen.kt:1-23; app/src/main/java/com/kipu/app/feature/auth/presentation/RegisterScreen.kt:1-22
- **Qué está mal:** `LoginScreen.kt` y `RegisterScreen.kt` son archivos independientes de 23 y 22 líneas que no contienen lógica propia y se limitan a llamar a `AuthFormScreen` con `initialRegisterMode` en false o true. `AuthFormScreen` ya contiene la lógica de ambas pantallas y la pestaña de alternancia.
- **Escenario de fallo:** Indirección innecesaria que obliga a mantener firmas y parámetros duplicados en dos archivos adicionales.
- **Fix mínimo:** Invocar `AuthFormScreen` directamente desde `AuthNavigation.kt` y retirar los wrappers redundantes `LoginScreen` y `RegisterScreen`.
- **Ahorro estimado:** 45 líneas.

#### [SEC2-17] Import no utilizado de `androidx.compose.ui.graphics.Color` en `SignOutDialog.kt`
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/auth/presentation/SignOutDialog.kt:10
- **Qué está mal:** `import androidx.compose.ui.graphics.Color` no se utiliza en ninguna parte del archivo `SignOutDialog.kt`.
- **Escenario de fallo:** Advertencia de compilador y código residual sin uso.
- **Fix mínimo:** Eliminar la línea de import.
- **Ahorro estimado:** 1 línea.

### Billing y planes

Porciones: BIL1 (Codex gpt-6.1-sol medium). Hallazgos: 9 (6 alta, 3 media).

#### [BIL1-01] La primera verificación puede adjudicar la compra a otra cuenta
- **Severidad:** alta · **Categoría:** seguridad · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/plans/data/billing/BillingRepository.kt:111-123; supabase/functions/verify-purchase/google-play-api.ts:241-345; supabase/functions/verify-purchase/index.ts:232-245
- **Qué está mal:** Al lanzar la compra se envía un identificador derivado del usuario a Play, pero el adaptador servidor descarta la identidad de cuenta que devuelve el proveedor. El callback y la restauración toman la sesión actual; solo se comprueba un propietario previo si el hash del token ya fue registrado.
- **Escenario de fallo:** A inicia un pago pendiente y cambia a B antes de su aprobación; B abre compras y restaura ese token todavía no registrado → la primera persistencia lo vincula a B y las restauraciones posteriores de A encuentran `TOKEN_ACCOUNT_CONFLICT`. No hace falta sustraer un token: basta compartir el dispositivo y su cuenta de Play.
- **Fix mínimo:** Conservar la identidad de cuenta del resultado verificado de Play y compararla en servidor con el hash esperado del usuario autenticado antes de la primera vinculación. Rechazar el desacuerdo; definir expresamente la compatibilidad para compras antiguas sin identificador.
- **Verificación (Fable):** El cliente envía `obfuscatedAccountId`, pero ninguna edge function lo compara con el usuario.

#### [BIL1-02] Lifetime lee el estado de otro formato de respuesta
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable
- **Ubicación:** supabase/functions/verify-purchase/google-play-api.ts:246-278
- **Qué está mal:** Se consulta `purchases/productsv2`, pero se lee `details.purchaseState` en la raíz. El estado de ese recurso se encuentra en `purchaseStateContext.purchaseState`; al no hallarlo se transforma la cadena vacía en `REVOKED` y, simultáneamente, se devuelve `purchaseState: PURCHASED`.
- **Escenario de fallo:** Una respuesta de compra única con `productLineItem` coincidente y `purchaseStateContext: { purchaseState: "PURCHASED" }` → una compra Lifetime pagada se persiste como revocada y no concede Premium. Un estado anidado `PENDING` también termina como compra adquirida/revocada y entra indebidamente en la ruta de reconocimiento.
- **Fix mínimo:** Leer y validar el estado anidado del recurso v2; rechazar formatos desconocidos antes de persistir o reconocer. Añadir un test del adaptador con esa respuesta JSON, en vez de simular directamente un `ProviderPurchase` ya normalizado.
- **Verificación (Fable):** Lee `details.purchaseState` en la respuesta `productsv2`; no pude contrastar el esquema de Google sin red.

#### [BIL1-03] El pago deja de procesarse al salir de la pantalla
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/plans/data/billing/BillingRepository.kt:36-48; app/src/main/java/com/kipu/app/feature/plans/data/billing/PlayBillingGateway.kt:34-35,101-103
- **Qué está mal:** La verificación es un efecto del Flow que recoge la pantalla de compra. `MutableSharedFlow` no conserva eventos sin suscriptores; su capacidad adicional no equivale a replay. El refresco automático de entitlement solo consulta la proyección del servidor, sin recuperar compras de Play no registradas.
- **Escenario de fallo:** El usuario recibe `PENDING`, vuelve al inicio y el ViewModel se destruye; después se aprueba el pago → el callback se descarta y no se ejecutan verificación ni reconocimiento. Si el RTDN llega antes de la primera vinculación queda esperando propietario/token, y usar el resto de la app no recupera la compra; se requiere abrir compras o restaurar manualmente, con riesgo de reembolso por falta de reconocimiento.
- **Fix mínimo:** Ejecutar el procesamiento de callbacks en un scope de aplicación e iniciar una recuperación de compras al recuperar una sesión/en primer plano. Mantener la UI como observadora de resultados y los tokens únicamente en memoria.
- **Verificación (Fable):** `purchaseResults` es un `transform` frío sobre un `SharedFlow` sin replay: sin colector, `tryEmit` descarta la compra hasta el próximo `requeryPurchases`.

#### [BIL1-04] La Edge Function rechaza el cupo aprobado de cinco más cinco
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** supabase/functions/plans/index.ts:69-75,91-98
- **Qué está mal:** `CUSTOM_CATEGORIES` tiene límite total 5 antes de llegar a la RPC. El llamador `saveSelectedFreeCategoryRoots` permite cinco raíces de gasto y cinco de ingreso y envía ambas en una única selección; la RPC versionada admite hasta diez y valida el límite por tipo.
- **Escenario de fallo:** Tras bajar a Free, el usuario selecciona cinco categorías de gasto y una de ingreso → Room guarda seis elementos válidos, pero `/plans/quota-selection` responde `INVALID_REQUEST`; el worker termina en fallo y la selección no converge en el segundo dispositivo.
- **Fix mínimo:** Cambiar a 10 el máximo de elementos de `CUSTOM_CATEGORIES` en esta validación y conservar la validación de cinco por tipo de la RPC. Añadir un test de la función que acepte diez IDs distintos de categorías y rechace once.
- **Verificación (Fable):** `quotaLimits.CUSTOM_CATEGORIES = 5` en `plans/index.ts` frente al cupo 5+5 de la migración 20261007144500.

#### [BIL1-05] Una suscripción cancelada pero pagada no recibe grant al restaurarse
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable
- **Ubicación:** supabase/functions/verify-purchase/index.ts:308-317; app/src/main/java/com/kipu/app/feature/plans/data/billing/BillingRepository.kt:134-137
- **Qué está mal:** El adaptador representa la cancelación vigente como `CANCELLED` + `CANCELED_ACTIVE`; la persistencia devuelve Premium mientras quede periodo pagado. La emisión de grant, sin embargo, exige que la compra candidata tenga estado `PURCHASED`, aunque el acceso agregado de la cuenta sea válido.
- **Escenario de fallo:** Se cancela la renovación de una suscripción que vence mañana y se restaura en otra instalación → el servidor responde `VERIFIED`, `effectivePremium: true` y grant nulo; Android devuelve siempre `OFFLINE_GRANT_UNAVAILABLE` y no instala acceso, aun con el periodo pagado vigente.
- **Fix mínimo:** Emitir el grant desde el acceso efectivo persistido para todo candidato no pendiente verificado, incluyendo `CANCELED_ACTIVE`, con el fin agregado como límite. Añadir un caso de cancelación vigente que compruebe tanto la respuesta del servidor como la escritura de cache en Android.

#### [BIL1-06] El refresco periódico puede quitar acceso durante gracia o cancelación vigente
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/migrations/20261102034340_dev_plan_mode.sql:166-192; app/src/main/java/com/kipu/app/feature/plans/data/entitlement/TestPlanRefreshRepository.kt:49-55,87
- **Qué está mal:** Referencia de contrato para SQL4: la última definición localizada de `get_effective_plan_for_refresh` filtra únicamente `ACTIVE` + `PURCHASED`. El consumidor reemplaza su cache por Free cuando recibe esa proyección, mientras el contrato de billing también concede acceso a gracia y cancelación con periodo vigente.
- **Escenario de fallo:** Una compra queda verificada en `IN_GRACE_PERIOD` o `CANCELED_ACTIVE`, con vencimiento futuro y sin otro entitlement → un refresco automático obtiene Free y reemplaza el grant vigente.
- **Fix mínimo:** SQL4 debe comprobar y alinear el predicado de esa proyección con el de acceso comercial ya usado por la verificación; conservar los límites de vigencia y probar un periodo cancelado aún pagado. No se amplió esta revisión a la auditoría de migraciones.
- **Cross-módulo:** sí → SQL4

#### [BIL1-07] Las preferencias esperando autenticación nunca vuelven a enviarse
- **Severidad:** media · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/plans/data/local/PlanPreferencesDao.kt:35-47; app/src/main/java/com/kipu/app/feature/plans/data/sync/SyncPlanSelectionWorker.kt:48-50,66-68
- **Qué está mal:** El worker cambia las filas a `WAITING_FOR_AUTH` y termina con éxito al no quedar filas `PENDING`. La siguiente ejecución solo selecciona `PENDING`; no existe un llamador de producción que reactive las filas esperando autenticación, y `ensurePeriodic` tampoco tiene llamadores de producción.
- **Escenario de fallo:** Una intención queda pendiente offline y se cierra sesión conservando cambios, o el envío recibe 401 → al volver a iniciar sesión con la misma cuenta, la intención sigue contándose como pendiente pero nunca se envía. Incluso ejecutar de nuevo el worker no la selecciona.
- **Fix mínimo:** Reponer a `PENDING` las filas de ese propietario al disponer de su sesión válida y encolar su sincronización. Añadir un test que recorra 401 → nueva sesión del mismo usuario → envío y estado `COMPLETED`.

#### [BIL1-08] La respuesta antigua pisa la elección local más reciente
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/plans/data/local/PlanPreferencesDao.kt:51-57
- **Qué está mal:** `reconcile` reemplaza incondicionalmente la preferencia local con la respuesta del servidor, sin comprobar si ya se emitió una revisión local posterior. La transacción protege las escrituras, pero no evita aplicar una respuesta antigua sobre un cambio nuevo.
- **Escenario de fallo:** Se envía revisión 1 con Free; mientras viaja, se confirma revisión 2 con intención Premium; llega la respuesta de revisión 1 y falla la red antes de enviar la segunda → la app vuelve a mostrar Free aunque su última elección y el outbox pendiente dicen Premium.
- **Fix mínimo:** Actualizar la revisión aceptada y completar la operación, pero conservar la preferencia local cuando exista una revisión emitida posterior que todavía no esté reconciliada. Añadir un test DAO con dos confirmaciones y respuesta únicamente para la primera.

#### [BIL1-09] Los tests de esquema prueban versiones obsoletas y otra ruta de fallo
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/plans/data/local/KipuDatabaseSchemaTest.kt:105-112,132-147,181-188
- **Qué está mal:** La prueba de reapertura migra hasta 25 y registra migraciones solo hasta esa versión, pero `KipuDatabase` declara versión 40; la reapertura no puede probar el camino exitoso actual. Además, la prueba llamada `newerUnknownSchemaFailsSafelyWithoutDeletingRepresentativeData` crea versión 11, que es anterior, por lo que verifica una subida sin migración y no el rechazo de una versión futura.
- **Escenario de fallo:** Con la base de versión 40, el test de reapertura encuentra que falta la ruta 25 → 40; por separado, una regresión que permitiese borrar datos al abrir una versión futura podría pasar el test del sentinel, porque nunca ejercita un downgrade real.
- **Fix mínimo:** Usar la lista de migraciones de producción hasta la versión actual para la reapertura y crear el sentinel con una versión superior a la actual para el otro caso. El test mínimo faltante es abrir ese sentinel futuro y verificar que sus datos sobreviven al rechazo.

### Categorías y comercios

Porciones: CAT1 (Codex gpt-6.1-terra medium (2 pasadas)); CAT2 (Gemini 3.8 flash-high). Hallazgos: 30 (8 alta, 14 media, 8 baja).

#### [CAT1-01] Un conflicto remoto recibe un identificador local inventado
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt:118-133; app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt:935-984
- **Qué está mal:** Ante un conflicto de presentación, el servidor crea su propio registro, pero el worker crea otro con un UUID aleatorio y sin la versión remota real. La resolución envía ese UUID local al servidor y oculta el conflicto local antes de confirmar el resultado.
- **Escenario de fallo:** Dos dispositivos editan la misma presentación con revisiones distintas → el segundo ve un conflicto; al resolverlo, el servidor no encuentra el UUID inventado y el conflicto desaparece localmente sin resolverse.
- **Fix mínimo:** Recuperar el conflicto remoto con su ID y versiones reales, y marcarlo resuelto localmente solo después de aceptar la resolución remota.

#### [CAT1-02] La cola puede quedar pendiente sin otro worker
- **Severidad:** alta · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/data/sync/CategorySyncScheduler.kt:27-40; app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt:83-105
- **Qué está mal:** El worker toma una sola instantánea de comandos y el programador usa KEEP, que descarta una nueva solicitud mientras existe trabajo activo.
- **Escenario de fallo:** El usuario edita una categoría después de que el worker leyó la cola pero antes de terminar → KEEP descarta el nuevo trabajo, el worker acaba y el comando queda pendiente hasta otro evento que programe sincronización.
- **Fix mínimo:** Encadenar una ejecución posterior cuando se agrega trabajo durante una ejecución, o usar un mecanismo que garantice un nuevo drenaje tras el worker activo.

#### [CAT1-03] Cambios consecutivos de reglas quedan en conflicto o marcados como sincronizados prematuramente
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt:581-615,698-734; app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt:156-179,213-250
- **Qué está mal:** Cada edición offline usa la revisión local optimista y la respuesta de cada comando cambia el estado y la revisión de la fila actual sin comprobar si ya existe una edición posterior.
- **Escenario de fallo:** Crear una regla, editarla y borrarla offline antes de sincronizar hace que el primer acuse reemplace la revisión de la fila más reciente y la marque SYNCED aunque los otros comandos sigan pendientes; una hidratación o edición concurrente puede tratar ese último cambio como confirmado.
- **Fix mínimo:** Aplicar el acuse solo si corresponde a la versión local enviada y conservar PENDING mientras haya comandos posteriores para la misma regla.

#### [CAT1-04] Los cambios de reglas de otro dispositivo no se descargan durante la sincronización normal
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt:72-84; app/src/main/java/com/kipu/app/feature/categories/data/sync/CategoryHydration.kt:30-33
- **Qué está mal:** La sincronización normal descarga categorías y catálogo, pero no alias ni preferencias. Existe una descarga suplementaria para la restauración explícita, no invocada por este worker.
- **Escenario de fallo:** Con dos dispositivos ya iniciados, se crea o elimina una preferencia en A y se sincroniza B → B conserva su copia anterior y aplica una clasificación diferente hasta restaurar los datos.
- **Fix mínimo:** Incorporar la descarga y reconciliación de reglas al ciclo normal de sincronización por usuario, respetando ediciones pendientes.
- **Verificación (Fable):** El worker solo llama `hydrateCategories` + `hydrateMerchantCatalog`; las reglas solo se bajan en `hydrateFully`.

#### [CAT1-07] La primera personalización deja una revisión local adelantada
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt:117-126,427-475; app/src/main/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorker.kt:115-138
- **Qué está mal:** La presentación inicial local empieza en revisión 1 y la primera edición la avanza a 2. Si aún no existe presentación remota, el RPC la crea con revisión 1 y el worker no reconcilia la revisión local.
- **Escenario de fallo:** El usuario personaliza por primera vez una categoría de sistema, sincroniza y vuelve a editarla → envía revisión 2 mientras el servidor tiene 1 y se produce un conflicto espurio.
- **Fix mínimo:** Reconciliar la revisión real confirmada por el servidor, sin sobrescribir ediciones posteriores pendientes.

#### [CAT2-01] Subcategorías inactivas se muestran como activas y no pueden reactivarse en la interfaz
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt:372, 670, 850, 933-953
- **Qué está mal:** La lista principal filtra únicamente por la condición de categoría raíz activa (`displayCategories.filter { it.category.isActive }`). Dentro de `CategoryRootCard`, `item.subcategories.forEach` itera sobre todas las subcategorías sin filtrar `subItem.category.isActive`. Como la raíz padre está activa, la condición `if (!isRootActive)` de la línea 946 nunca se cumple y la subcategoría inactiva se renderiza idéntica a una activa. Además, el menú contextual de subcategoría (líneas 933-942) ofrece únicamente "Inactivar", el callback `onToggleSubcategoryActive` (línea 670) nunca se invoca ni se suministra desde la pantalla, y la sección inferior "Inactivas" solo lista raíces (`displayCategories.filterNot { it.category.isActive }`).
- **Escenario de fallo:** El usuario inactiva la subcategoría "Restaurantes" dentro de la categoría raíz activa "Alimentación". Al refrescar la pantalla, "Restaurantes" sigue apareciendo en la lista desplegada de "Alimentación" sin ninguna indicación visual de inactiva; su menú contextual solo ofrece "Inactivar" (que no produce ningún efecto); el contador de "Inactivas" al pie marca 0; y el usuario no tiene ninguna vía en la interfaz para volver a reactivar su subcategoría.
- **Fix mínimo:** Filtrar `item.subcategories.filter { it.category.isActive }` para la vista normal de la tarjeta raíz; en el menú contextual de subcategoría, conmutar entre "Inactivar" y "Reactivar" según `subItem.category.isActive` invocando `viewModel.toggleCategoryActive(subItem.category.id, subItem.category.isActive)`; y si la sección "Inactivas" incluye subcategorías huérfanas o inactivas, mostrarlas bajo su padre con etiqueta explícita de inactiva.

#### [CAT2-02] `CategoryPresentationViewModel` no consume el Flow de presentaciones y envía revisiones desfasadas provocando conflictos espurios
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryPresentationViewModel.kt:79-90, 133-136
- **Qué está mal:** En `loadCategoryData()`, la línea 81 ejecuta `val presentation = category?.let { repository.observeCategoryPresentations(userId) }`, instanciando un `Flow` que nunca se colecta ni se asigna al estado. El estado UI se actualiza manteniendo `name = ""` y valores por defecto arbitrarios. Adicionalmente, asigna `expectedRevision = category?.revision ?: 1L`, confundiendo la revisión de la entidad `Category` con la revisión de `CategoryPresentation`. Al guardar (línea 136), `updateCategoryPresentation` compara `expectedRevision` contra `existing.remoteRevision` de la presentación en Room (`OfflineFirstCategoriesRepository.kt:450`); al discrepar, Room inserta inmediatamente un `CategoryConflictEntity` en vez de actualizar la presentación.
- **Escenario de fallo:** Se abre `CategoryPresentationEditor` para una categoría existente con nombre "Compras" y revisión de presentación 3 (mientras la categoría base está en revisión 1). La pantalla muestra el nombre vacío. Si el usuario ingresa un nombre y pulsa "Guardar Cambios", envía `expectedRevision = 1`. El repositorio detecta la discrepancia con la revisión 3 de la presentación en base de datos e inserta un conflicto de sincronización bloqueante en estado OPEN sin actualizar los datos locales.
- **Fix mínimo:** Colectar la presentación actual desde `repository.observeCategoryPresentations(userId)` tomando el primer elemento que coincida con `cid`, poblar `name`, `icon`, `color` y fijar `expectedRevision = presentation.revision`.
- **Verificación (Fable):** El Flow de presentaciones no se colecta (variable sin uso) y la revisión enviada es la de la categoría.

#### [CAT2-03] `CategoryFormDialog` oculta errores de guardado y permite pulsaciones concurrentes sin bloqueo
- **Severidad:** alta · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesViewModel.kt:379-447; app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryFormScreen.kt:92-152, 650-678
- **Qué está mal:** `CategoryFormDialog` (ModalBottomSheet) no recibe ni propaga el parámetro `errorMessage` a `CategoryFormContent`. Cuando `createCategory` o `updateCategoryPresentation` fallan, el ViewModel actualiza `_uiState.errorMessage`, el cual dispara un Snackbar en el `Scaffold` que queda completamente oculto detrás de la hoja modal abierta. Además, `CategoriesUiState` no cuenta con bandera de `isSaving`/`isCreating`, por lo que el botón "Guardar categoría" permanece activo durante la operación asíncrona y `submitCreateCategory()` no comprueba si ya existe un guardado en curso.
- **Escenario de fallo:** En un dispositivo con Room saturado o retraso de I/O, el usuario presiona dos veces seguidas "Guardar categoría". Se lanzan dos corrutinas concurrentes creando categorías duplicadas con el mismo nombre; si una de ellas falla por restricción de unicidad, el error se emite a un Snackbar oculto detrás de la hoja modal: la hoja no se cierra y el usuario no recibe ningún mensaje explicativo.
- **Fix mínimo:** Añadir `isSaving: Boolean = false` y `errorMessage: String? = null` a `CategoriesUiState`, propagar ambos a `CategoryFormDialog` -> `CategoryFormContent`, inhabilitar el botón de confirmación mientras `isSaving` sea verdadero, y en `submitCreateCategory()` retornar inmediatamente si `_uiState.value.isSaving` es true.

#### [CAT1-05] La búsqueda de comercios comprueba solo la primera página del catálogo
- **Severidad:** media · **Categoría:** escala · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/functions/merchant-search/index.ts:155-162,244-257
- **Qué está mal:** La comprobación por dominio descarga el catálogo sin paginación ni filtro por los dominios candidatos; la respuesta REST puede estar limitada a la primera página.
- **Escenario de fallo:** Con un catálogo mayor que el límite de filas de la API, un dominio existente fuera de la primera página aparece como no incluido, y la interfaz ofrece agregarlo de nuevo.
- **Fix mínimo:** Consultar únicamente los dominios de los candidatos con un filtro de conjunto, o paginar toda la respuesta.

#### [CAT1-06] El borrado de clasificación no entra en la cola de sincronización
- **Severidad:** media · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/data/OfflineFirstCategoriesRepository.kt:906-915; app/src/main/java/com/kipu/app/feature/categories/domain/usecase/UpdateMovementClassification.kt:40-45
- **Qué está mal:** Las operaciones públicas de quitar categoría o comercio solo modifican Room y devuelven éxito; no crean comando remoto. No encontré llamadores de estas dos operaciones de caso de uso en el código de producción actual.
- **Escenario de fallo:** Si un consumidor llama clearCategory para un movimiento previamente sincronizado, el dato desaparece localmente pero permanece en el servidor y en otro dispositivo.
- **Fix mínimo:** Encauzar el borrado por la actualización de clasificación sincronizada o retirar estas operaciones si no forman parte del contrato usado.

#### [CAT1-08] La pantalla no carga la preferencia al seleccionar el comercio
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/merchantrules/MerchantCategoryPreferenceViewModel.kt:96-115; app/src/main/java/com/kipu/app/feature/categories/presentation/merchantrules/MerchantCategoryPreferenceScreen.kt:286-295
- **Qué está mal:** Al seleccionar un comercio se busca su preferencia, pero no se actualiza `existingPreference`; solo cambia cuando otra fuente del flujo combinado emite.
- **Escenario de fallo:** Abrir la pantalla y seleccionar un comercio que ya tiene preferencia, sin otro cambio del catálogo → no aparece el botón para eliminarla; al cambiar desde otro comercio puede conservar el estado anterior.
- **Fix mínimo:** Establecer `existingPreference` junto con el comercio seleccionado y limpiarla al cambiar la búsqueda.

#### [CAT1-09] Una actualización puede descartar la categoría elegida sin guardar
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/merchantrules/MerchantCategoryPreferenceViewModel.kt:117-120,158-200
- **Qué está mal:** Cada emisión de catálogo, categorías o preferencias prioriza la preferencia guardada sobre la selección actual del formulario.
- **Escenario de fallo:** La preferencia guardada es A, el usuario selecciona B y la hidratación emite un catálogo actualizado antes de guardar → la pantalla regresa a A y guarda el valor anterior.
- **Fix mínimo:** Mantener el borrador mientras sea elegible y cargar la preferencia guardada al seleccionar el comercio o confirmar el guardado.

#### [CAT1-10] Distintos identificadores de logo pueden reutilizar el mismo archivo
- **Severidad:** media · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/functions/merchant-logo-sync/index.ts:141-144,199-203,240-265; supabase/functions/merchant-logo-sync/store.ts:124-148
- **Qué está mal:** La ruta se deriva de una versión simplificada del ETag, que cambia mayúsculas, reemplaza caracteres y corta a 64 caracteres. Si dos ETags producen la misma ruta, se acepta el archivo existente y se publica el ETag nuevo con la imagen antigua.
- **Escenario de fallo:** Para un dominio el proveedor cambia el ETag de `ABC` a `abc` y cambia la imagen → ambos producen `abc.webp`, se conserva el objeto anterior y se marca READY con el logo viejo.
- **Fix mínimo:** Usar un hash estable del ETag completo en el nombre del objeto.

#### [CAT1-11] El lote de logos solo procesa una página de comercios
- **Severidad:** media · **Categoría:** escala · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** supabase/functions/merchant-logo-sync/store.ts:90-100; supabase/functions/merchant-logo-sync/index.ts:321-337
- **Qué está mal:** El listado general no pagina la consulta REST, aunque el proceso recibe el resultado como todo el catálogo.
- **Escenario de fallo:** Con más comercios que el límite de filas REST, las ejecuciones generales procesan solo la primera página ordenada por dominio; las filas posteriores no reciben ni renuevan su logo en esas ejecuciones.
- **Fix mínimo:** Recorrer todas las páginas ordenadas o procesar lotes con un cursor estable.

#### [CAT1-12] Una prueba del worker verifica una llamada que ya no ocurre
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/categories/data/sync/SyncCategoryCommandsWorkerTest.kt:122; app/src/main/java/com/kipu/app/feature/categories/data/sync/CategoryHydration.kt:100-112
- **Qué está mal:** La prueba espera `insertPresentationsIfAbsent`, pero la hidratación actual inserta cada presentación con `insertPresentation`.
- **Escenario de fallo:** Con las tres categorías del fixture y ninguna presentación existente, el worker ejecuta tres inserciones individuales y no satisface la verificación de inserción por lote.
- **Fix mínimo:** Verificar las tres presentaciones insertadas mediante la ruta actual y sus valores observables; ejecutar la prueba focalizada cuando se permita usar dispositivo.

#### [CAT2-04] Búsqueda de categorías en `CategoriesScreen` falla ante nombres con tildes al no normalizar diacríticos
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt:348-356, 1120-1128
- **Qué está mal:** La búsqueda en `CategoriesScreen` filtra mediante `rootItem.displayName.lowercase().contains(q)` y resalta con `indexOf(query.trim(), ignoreCase = true)`. A diferencia de `MerchantPicker` y `CategoryRules.normalizeText()`, no aplica descomposición canónica NFD ni elimina marcas diacríticas.
- **Escenario de fallo:** Un usuario en Perú escribe "alimentacion", "musica" o "educacion" (sin tilde, habitual en teclados móviles) en la barra de búsqueda de categorías. Las categorías predeterminadas del catálogo "Alimentación", "Música" y "Educación" se descartan por completo y la pantalla muestra "Resultados para ... · 0".
- **Fix mínimo:** Reemplazar el filtrado manual por `CategoryRules.matchesNormalized(rootItem.displayName, searchQuery)` y normalizar de igual forma las subcategorías.

#### [CAT2-05] Triple consulta redundante a Room por cada carácter ingresado en el buscador de comercios
- **Severidad:** media · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantPickerViewModel.kt:73-91; app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantPicker.kt:388-390, 400-406, 559; app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantDiscoveryViewModel.kt:36-38
- **Qué está mal:** Por cada tecla pulsada en `MerchantPicker`: (1) `MerchantPickerViewModel.onQueryChanged` dispara `searchMerchantCatalog(query)` consultando Room vía Flow; (2) el `LaunchedEffect(state.query)` en `MerchantPicker` notifica a `discovery.onQueryChanged(query)`, que lanza otra consulta a Room vía `catalog.searchMerchants(query)`; (3) la línea 404 de `MerchantPicker` evalúa `val baseList = if (state.catalogEntries.isNotEmpty()) state.catalogEntries else state.searchResults` y filtra `baseList` en memoria. Al estar `catalogEntries` precargado con todos los comercios activos desde `init`, los resultados de ambas consultas Room (1 y 2) son sistemáticamente ignorados.
- **Escenario de fallo:** Al escribir "tambo" (5 caracteres), se disparan 10 consultas asíncronas a SQLite/Room cuyos resultados se descartan sin mostrarse nunca en pantalla, consumiendo innecesariamente hilos del dispatcher de I/O y memoria.
- **Fix mínimo:** Eliminar la llamada a `catalog.searchMerchants(query)` en `MerchantDiscoveryViewModel.onQueryChanged` (usando el catálogo en memoria) y unificar la fuente de búsqueda en `MerchantPickerViewModel`.

#### [CAT2-06] `LazyColumn` anidada dentro de `Column(verticalScroll)` en `MerchantPickerBottomSheet` genera conflictos de scroll
- **Severidad:** media · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantPicker.kt:308-314, 526-531
- **Qué está mal:** `MerchantPickerBottomSheet` contiene un `Column` con modificador `.verticalScroll(rememberScrollState())`. A su vez, el `MerchantPicker` embebido contiene un `LazyColumn` con altura acotada (`heightIn(max = listHeight)`). En Compose, anidar listas perezosas dentro de un contenedor desplazable dentro de un `ModalBottomSheet` produce colisiones de captura de gestos táctiles.
- **Escenario de fallo:** Al abrir el selector de comercios en un dispositivo físico y arrastrar verticalmente la lista de comercios para ver más resultados, el gesto es interceptado por el scroll del contenedor padre o por el swipe-down de cierre del `ModalBottomSheet`, cerrando la hoja accidentalmente o impidiendo el scroll fluido de los comercios.
- **Fix mínimo:** Quitar el modificador `verticalScroll` del contenedor raíz en `MerchantPickerBottomSheet` y dejar que `LazyColumn` maneje el desplazamiento vertical internamente.

#### [CAT2-07] Pantallas y ViewModel huérfanos sin registro en rutas de navegación
- **Severidad:** media · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryConflictScreen.kt:52-150; app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryPresentationEditor.kt:77-303; app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryPresentationViewModel.kt:42-187
- **Qué está mal:** Ninguno de estos 3 componentes está registrado en `SettingsNavigation.kt`, `AppNavHost` ni en ningún punto de la navegación de la app. `CategoryPresentationEditor` fue reemplazado funcionalmente por `CategoryFormDialog` dentro de `CategoriesScreen.kt`. `CategoryConflictScreen` es la única pantalla diseñada para resolver `CategoryConflictEntity`; al no tener ruta de navegación, si se produce un conflicto en Room, el usuario nunca dispone de interfaz para resolverlo.
- **Escenario de fallo:** Si se genera un conflicto de sincronización en Room, la entidad queda abierta indefinidamente porque el usuario no puede acceder a `CategoryConflictScreen`. `CategoryPresentationEditor` acumula 322 líneas de código muerto que confunden el mantenimiento del módulo.
- **Fix mínimo:** Registrar la ruta para resolución de conflictos en `SettingsNavigation.kt` si el producto requiere resolver conflictos manualmente, y eliminar `CategoryPresentationEditor.kt` junto con los métodos obsoletos de `CategoryPresentationViewModel`.
- **Cross-módulo:** sí → NAV1 / UI2
- **Ahorro estimado:** 734 líneas
- **Verificación (Fable):** `CategoryConflictScreen` y `CategoryPresentationEditor` no tienen ruta. Decidido el 2026-11-11: los conflictos de presentación se resuelven solos y la pantalla se borra (§8.12).

#### [CAT2-10] Modal de selección de cupo Free bloqueado cuando el cupo está exactamente en su límite (5/5)
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt:324-326
- **Qué está mal:** En `CategoriesScreen.kt`, el callback para abrir el diálogo de selección de cupo en `QuotaBanner` se asigna con: `if (state.activeCustomExpenseRootsCount > state.maxCustomRoots || state.activeCustomIncomeRootsCount > state.maxCustomRoots) viewModel::openQuotaSelection else null`. Al usar comparador estrictamente mayor que (`>`), no se ofrece acceso cuando el usuario tiene exactamente 5 categorías activas.
- **Escenario de fallo:** Un usuario Free tiene 5 categorías activas de gasto (5/5) y desea cambiar cuáles 5 están activas en su dispositivo mediante el modal `openQuotaSelection`. El botón con el chevron hacia el modal no aparece porque `5 > 5` es falso. El usuario no puede reconfigurar su selección a menos que desactive una categoría o supere el límite.
- **Fix mínimo:** Cambiar la condición a `>= state.maxCustomRoots` o habilitar el acceso a `openQuotaSelection` siempre que existan raíces personalizadas activas.

#### [CAT2-13] Ausencia total de pruebas unitarias para `MerchantPickerViewModel`
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantPickerViewModel.kt:1-127
- **Qué está mal:** `MerchantPickerViewModel` implementa la exclusividad crítica entre `selectedMerchant` y `provisionalText`, la limpieza de consultas y la reactividad del catálogo. No existe ningún archivo `MerchantPickerViewModelTest.kt` en toda la suite de pruebas.
- **Escenario de fallo:** Un cambio inadvertido en `selectMerchant()` olvida limpiar `provisionalText` o en `setProvisionalText()` olvida limpiar `selectedMerchant`, permitiendo que coexistan ambos valores y generando inconsistencias en el registro de movimientos en `QuickMovementScreen`, `MovementEditorSheet` y `CaptureReviewScreen`. Ningún test unitario detecta el fallo.
- **Fix mínimo:** Crear `app/src/test/java/com/kipu/app/feature/categories/presentation/MerchantPickerViewModelTest.kt` con pruebas que verifiquen: (1) `selectMerchant` limpia `provisionalText` y fija el comercio; (2) `setProvisionalText` limpia `selectedMerchant` y asigna el texto recortado; (3) `clearSelection` limpia ambos.

#### [CAT2-14] Casos de mutación en `CategoriesViewModel` carecen de pruebas unitarias
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/feature/categories/presentation/categories/CategoriesViewModelTest.kt:73-77
- **Qué está mal:** `CategoriesViewModelTest` utiliza mocks relajados (`relaxed = true`) para `createCategory`, `setCategoryActive`, `updateCategoryPresentation` y `saveSelectedFreeCategoryRoots`, pero ninguno de sus tests invoca ni valida `submitCreateCategory()`, `confirmDeleteCategory()`, `toggleCategoryActive()` ni `saveQuotaSelection()`.
- **Escenario de fallo:** Una regresión en `submitCreateCategory()` omite enviar `categoryType` o intercambia `ownerId`, o `confirmDeleteCategory()` deja de llamar a `setCategoryActive(id, false)`. La suite de pruebas de `CategoriesViewModelTest` pasa completamente en verde.
- **Fix mínimo:** Añadir pruebas unitarias específicas en `CategoriesViewModelTest` que invoquen `submitCreateCategory()` (tanto en modo creación como edición) y `confirmDeleteCategory()`, verificando con `coVerify` que los casos de uso reciben los parámetros correctos.

#### [CAT2-08] Código muerto en `CategoryDesignTokens.kt` (paletas no utilizadas y bucle inalcanzable)
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/CategoryDesignTokens.kt:55-116, 169-174
- **Qué está mal:**  1. `CATEGORY_PALETTE` (líneas 57-66) no se usa en todo el repositorio (`CategoryFormScreen` y `CategoryPresentationEditor` usan listas privadas de colores no alineadas con esta paleta). 2. `CATEGORY_ICON_SECTIONS` (líneas 79-116) y sus clases de datos `CategoryIconItem` y `CategoryIconSection` solo son leídas por el bloque `else` de `resolveCategoryIcon` (líneas 169-174). 3. Los 16 iconos de `CATEGORY_ICON_SECTIONS` ya están listados explícitamente en el `when` de `resolveCategoryIcon` (líneas 133-150), por lo que el bucle del bloque `else` jamás coincide con ningún valor.
- **Escenario de fallo:** 70 líneas de código y estructuras de datos estáticas que se cargan en memoria pero no tienen ningún consumidor activo ni ruta de ejecución posible.
- **Fix mínimo:** Eliminar `CATEGORY_PALETTE`, `CATEGORY_ICON_SECTIONS`, `CategoryColor`, `CategoryIconItem`, `CategoryIconSection` y el bucle en el `else` de `resolveCategoryIcon`, retornando directamente `Icons.Default.Label`.
- **Ahorro estimado:** 70 líneas

#### [CAT2-09] Insignias numéricas de las pestañas "Gastos" e "Ingresos" incluyen categorías inactivas falseando el conteo
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt:305-306
- **Qué está mal:** `expenseCount` e `incomeCount` se calculan como `CategoryTab.EXPENSE.filterCategories(state.categories).count { it.category.isRoot }`. `state.categories` contiene tanto categorías activas como inactivas, y `filterCategories()` solo discrimina por tipo (EXPENSE/INCOME/GENERAL), sin verificar `isActive`.
- **Escenario de fallo:** Un usuario tiene 8 categorías activas de gasto y 3 inactivadas. La pestaña "Gastos" muestra una insignia con el número "11". Al revisar la lista, el usuario solo observa 8 tarjetas visibles, generando aparente pérdida o desajuste de datos hasta que expande la sección inferior.
- **Fix mínimo:** Modificar el cálculo a: `CategoryTab.EXPENSE.filterCategories(state.categories).count { it.category.isRoot && it.category.isActive }`.

#### [CAT2-11] `CategoriesViewModel` re-implementa el conteo de raíces ignorando las funciones canónicas de `CategoryRules`
- **Severidad:** baja · **Categoría:** fusionar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesViewModel.kt:158-168
- **Qué está mal:** `CategoriesViewModel` calcula manualmente en un bucle `activeRoots`, `activeExpense` y `activeIncome`, duplicando la lógica centralizada en `CategoryRules.countActiveCustomRoots()`, `CategoryRules.countActiveCustomExpenseRoots()` y `CategoryRules.countActiveCustomIncomeRoots()`.
- **Escenario de fallo:** Si las reglas de imputación de cuotas (por ejemplo, el tratamiento de categorías GENERAL sobre los dos ejes) cambian en el dominio, el ViewModel mantendrá un cálculo desfasado respecto al repositorio y a Room.
- **Fix mínimo:** Reemplazar el conteo manual por llamadas a `CategoryRules.countActiveCustomRoots(categories)`, `CategoryRules.countActiveCustomExpenseRoots(categories)` y `CategoryRules.countActiveCustomIncomeRoots(categories)`.
- **Ahorro estimado:** 10 líneas

#### [CAT2-12] Etiqueta engañosa "Usa 1 cupo" para categorías personalizadas de tipo GENERAL
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt:759
- **Qué está mal:** La tarjeta muestra `Text(if (item.category.isCustom) "Usa 1 cupo" else "No consume cupo")`. De acuerdo con las reglas de negocio de la app (`CategoryRules`), una categoría `GENERAL` consume 1 cupo en el eje de gastos y 1 cupo en el eje de ingresos (2 cupos en total).
- **Escenario de fallo:** Un usuario Free con 4 categorías de gasto y 4 de ingreso crea una categoría General. La tarjeta le indica "Usa 1 cupo", pero inmediatamente ambas barras de gasto e ingreso se saturan al 5/5, provocando confusión sobre el consumo real de su plan.
- **Fix mínimo:** Ajustar el texto informativo a: `if (item.category.categoryType == CategoryType.GENERAL) "Usa 1 cupo en gastos e ingresos" else if (item.category.isCustom) "Usa 1 cupo" else "No consume cupo"`.

#### [CAT2-15] Sección "Inactivas (0)" se muestra de forma permanente aunque no existan categorías inactivas
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesScreen.kt:391-403
- **Qué está mal:** El elemento `item { Row(...) { Text("  Inactivas (${inactive.size})") } }` se inserta en el `LazyColumn` sin verificar si `inactive.isNotEmpty()`.
- **Escenario de fallo:** Un usuario nuevo o sin categorías dadas de baja ve siempre un botón "Inactivas (0)" con icono de papelera al final de su lista. Al pulsarlo, se expande una sección vacía sin contenido.
- **Fix mínimo:** Envolver la sección en `if (inactive.isNotEmpty()) { ... }`.

#### [CAT2-16] Valores por defecto de nueva categoría no forman parte de las listas rápidas en `CategoryFormContent`
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryFormScreen.kt:81-88, 519, 602; CategoriesViewModel.kt:68-69
- **Qué está mal:** `CategoriesViewModel` inicializa una nueva categoría raíz con icono `"restaurant"` y color `"#0F766E"` (Verde Kipu). Sin embargo, `"restaurant"` no está en `QUICK_ICONS` (`listOf("tv", "music_note", "directions_car", "content_cut", "receipt", "shopping_cart")`), y `"#0F766E"` no está en `QUICK_COLORS` (`listOf("#06B6D4", "#3B82F6", "#8B5CF6", "#F97316", "#F43F5E", "#10B981")`). Al abrir el diálogo, ninguno de los 6 iconos rápidos ni de los 6 colores rápidos aparece seleccionado; ambos seleccionan el 7º botón ("+") de opciones avanzadas.
- **Escenario de fallo:** El usuario abre "Nueva categoría" esperando una selección ágil entre las opciones visibles, pero los valores por defecto del sistema no coinciden con las opciones rápidas, forzando la selección sobre los botones de catálogo extendido.
- **Fix mínimo:** Incluir `"restaurant"` en `QUICK_ICONS` y `"#0F766E"` en `QUICK_COLORS`.

#### [CAT2-17] `CategoryPresentationEditor` tiene icono de carpeta hardcodeado y paleta desconectada del sistema de diseño
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryPresentationEditor.kt:61-65, 186-192
- **Qué está mal:** En la tarjeta de previsualización en vivo, el icono está fijado estáticamente a `Icons.Default.Folder`, ignorando `state.icon` y sin proporcionar selector de iconos. Asimismo, define una paleta privada de 10 colores (`PRESENTATION_PALETTE`) que no coincide con `CATEGORY_PALETTE` ni con `QUICK_COLORS`.
- **Escenario de fallo:** Al abrir este editor para una categoría de transporte o salud, la vista previa siempre muestra una carpeta genérica. (Nota: pantalla huérfana de navegación, candidata a borrado en CAT2-07).
- **Fix mínimo:** Reemplazar por `resolveCategoryIcon(state.icon)` o eliminar la pantalla huérfana.

#### [CAT2-18] Lista de opciones de filtrado en `MerchantPicker` se re-instancia en cada recomposición sin `remember`
- **Severidad:** baja · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantPicker.kt:422-426
- **Qué está mal:** `val groupOptions = listOf(...) + availableGroups.map { ... }` se evalúa directamente en el cuerpo del composable `MerchantPicker` sin `remember`. Con cada recomposición provocada por la escritura del usuario o cambios de foco, se crean y destruyen 12+ instancias de `FilterOption`.
- **Escenario de fallo:** Al escribir rápidamente un término de búsqueda, se generan asignaciones redundantes de memoria en el hilo principal durante la animación de la interfaz.
- **Fix mínimo:** Envolver la creación de `groupOptions` en `remember(availableGroups, filterColor) { ... }`.

### Metas

Porciones: GOA1 (Codex gpt-6.1-terra medium (2 pasadas)). Hallazgos: 13 (8 alta, 5 media).

#### [GOA1-01] La cola no respeta dependencias de la meta secundaria
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/goals/data/local/GoalOutboxDao.kt:30-40; app/src/main/java/com/kipu/app/feature/goals/data/local/GoalLocalDataSource.kt:364-375
- **Qué está mal:** Una reasignación o reemplazo modifica dos metas, pero el orden de envío solo compara el goal_id principal. No considera destination_goal_id ni replacement_goal_id.
- **Escenario de fallo:** Una reasignación A→B queda RETRYABLE durante 30 segundos; después se edita B offline. La edición tiene como revisión esperada el resultado optimista de la reasignación, pero puede enviarse antes y recibir REVISION_MISMATCH porque B aún conserva su revisión anterior en el servidor.
- **Fix mínimo:** Al elegir el siguiente comando, bloquearlo también por operaciones previas que afecten a cualquiera de sus metas secundarias. Añadir un caso Room con la reasignación en reintento y una edición del destino.

#### [GOA1-02] Eliminar con varias cuentas deja liberaciones parciales
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailViewModel.kt:144-165; app/src/main/java/com/kipu/app/feature/goals/data/OfflineFirstGoalRepository.kt:344-365
- **Qué está mal:** delete confirma cada liberación por separado y vuelve a pasar el mismo reemplazo en todas ellas. El repositorio rechaza ese reemplazo si una liberación concreta no necesita reactivar la meta; los cambios anteriores ya están persistidos.
- **Escenario de fallo:** Una meta COMPLETED tiene reserva en dos cuentas, dos metas activas Free y se elige una de ellas como reemplazo. La primera liberación baja de la cantidad objetivo y bloquea el reemplazo; la segunda devuelve UNEXPECTED_REPLACEMENT_GOAL. La eliminación falla después de liberar parte del dinero y bloquear otra meta.
- **Fix mínimo:** Validar y aplicar la eliminación lógica con todas sus liberaciones y el reemplazo en una única operación atómica. Usar el reemplazo solo en la transición que lo requiere y conservar toda la historia.

#### [GOA1-03] La reasignación omite al destino al reconocer resultados
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/goals/data/local/GoalLocalDataSource.kt:137-147,168-204; app/src/main/java/com/kipu/app/feature/goals/data/sync/GoalChangeFeedApplier.kt:128-141
- **Qué está mal:** El conjunto de metas afectadas reconoce la principal y replacement_goal_id, pero ignora destination_goal_id. La propagación de conflictos del feed repite esa omisión.
- **Escenario de fallo:** Se aplica optimistamente A→B y el servidor rechaza por una revisión obsoleta de B. Solo A queda CONFLICT/FAILED; B conserva su aporte y estado PENDING y puede recibir nuevas operaciones basadas en una reserva que el servidor no aceptó. En una respuesta aplicada sin lista goals, B tampoco se marca confirmada en este reconocimiento.
- **Fix mínimo:** Incluir destination_goal_id al calcular metas afectadas tanto en el reconocimiento como en el feed y resolver ambas proyecciones en cada resultado. Probar éxito y conflicto de una reasignación real con Room.

#### [GOA1-05] Un traslado rechazado sigue alterando los saldos locales
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/goals/data/local/GoalLocalDataSource.kt:194-200,288-344; app/src/main/java/com/kipu/app/feature/goals/data/sync/GoalChangeFeedApplier.kt:61-66
- **Qué está mal:** El traslado optimista se guarda ACTIVE con asientos que afectan saldos. Un rechazo remoto solo cambia el estado de la meta y del recibo; ni transacción, ni asientos, ni evento se excluyen del cálculo efectivo. Descartar conflictos financieros también se rechaza, por lo que esa acción no recupera los saldos.
- **Escenario de fallo:** Otro dispositivo consume el saldo de origen mientras este teléfono está offline; aquí se traslada y reserva dinero y el servidor lo rechaza por respaldo insuficiente. Este teléfono conserva el débito, el crédito y la reserva locales como efectivos aunque no exista el traslado remoto.
- **Fix mínimo:** Implementar una resolución explícita que, en una transacción local, conserve la evidencia rechazada y la excluya de saldos y reservas efectivos. Mostrar el motivo y permitir resolverla; preservar el historial no exige seguir contando una operación rechazada como dinero confirmado.

#### [GOA1-06] El reemplazo Free excluye metas de otra moneda
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/goals/presentation/GoalContributionSheet.kt:118-124; app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailScreen.kt:132-134,308-312
- **Qué está mal:** Las pantallas cuentan y ofrecen como reemplazo solo metas activas de la moneda actual. El repositorio cuenta todas las metas activas para el cupo y no impone esa restricción monetaria al reemplazo.
- **Escenario de fallo:** Una persona Free tiene una meta completada PEN y dos metas activas USD. Al retirar de la meta PEN o eliminarla, no aparece el selector de reemplazo y el repositorio devuelve REPLACEMENT_REQUIRED; la interfaz no permite proporcionar el dato exigido.
- **Fix mínimo:** Usar todas las metas activas para el cupo y el reemplazo. Mantener el filtro por moneda exclusivamente para reasignar dinero entre metas.

#### [GOA1-09] Un comando puede enviarse con la sesión de otro usuario
- **Severidad:** alta (el worker dijo crítica) · **Categoría:** seguridad · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/goals/data/sync/GoalsSyncScheduler.kt:74-100; app/src/main/java/com/kipu/app/feature/goals/data/remote/GoalsApi.kt:56-65
- **Qué está mal:** El worker valida el propietario una sola vez al arrancar. Cada RPC obtiene la sesión vigente sin comprobar que su usuario sea el dueño del comando; el payload tampoco transporta un propietario que permita detectar ese cambio.
- **Escenario de fallo:** A tiene pendiente CREATE_GOAL sin cuenta vinculada; el worker verifica A y queda suspendido antes de obtener la sesión del RPC. Se cierra A y se abre B; GoalsApi toma el token de B y create_goal_v1 crea bajo B la meta privada de A, mientras el recibo local se confirma bajo A. El contrato versionado deriva el propietario de auth.uid(), por lo que RLS no distingue esta petición legítimamente autenticada como B.
- **Fix mínimo:** Pasar el userId esperado a execute y rechazar antes del POST cualquier sesión que no coincida; comprobar también el propietario antes de reclamar comandos y antes de aplicar sus resultados, reutilizando requireSyncOwner. Conservar el comando pendiente al cambiar de propietario.
- **Verificación (Fable):** Confirmado: `GoalsApi.execute` usa `currentSession()` sin comparar el dueño, igual que DEB1-01. Misma severidad que DEB1-01: exige cambiar de cuenta durante una corrida.

#### [GOA1-10] Un recibo antiguo sobrescribe el estado de cambios posteriores
- **Severidad:** alta · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/goals/data/local/GoalLocalDataSource.kt:176-186; app/src/main/java/com/kipu/app/feature/goals/data/local/GoalDao.kt:158-162
- **Qué está mal:** Si la respuesta trae goals, se escribe incondicionalmente su status y SYNCED. Se conserva la revisión máxima, pero no el estado optimista de comandos posteriores; el control de countPending se ejecuta únicamente cuando no existe esa lista.
- **Escenario de fallo:** Offline se libera una meta completada con reemplazo Free y luego se reduce su objetivo para completarla otra vez. La respuesta de la primera operación llega con ACTIVE/revisión 5 cuando la proyección local ya es COMPLETED/revisión 6: queda ACTIVE/revisión 6/SYNCED. El reconocimiento simple de la segunda operación no restaura el status y el feed de revisión 6 puede fallar con GOAL_REVISION_REUSED.
- **Fix mínimo:** Aplicar el status remoto solo si no hay una proyección posterior pendiente para esa meta; mantener PENDING y el estado optimista cuando la revisión local es superior. Añadir un test Room con dos comandos locales y respuestas sucesivas.

#### [GOA1-11] Reasignar desde una meta completada puede exceder el cupo Free
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/goals/data/OfflineFirstGoalRepository.kt:394-468; app/src/main/java/com/kipu/app/feature/goals/domain/usecase/ReassignGoalReserve.kt:34-45
- **Qué está mal:** La reasignación puede reactivar la fuente completada, pero no comprueba el cupo ni solicita un reemplazo. A diferencia de releaseGoalReserve, este camino carece incluso de los parámetros para esa selección.
- **Escenario de fallo:** Free tiene A completada con S/100 reservados y B/C activas; B tiene un objetivo alto. Reasigna S/10 de A a B: A pasa a ACTIVE, B sigue ACTIVE y C también, dejando tres metas activas sin selección de reemplazo. SC-007 de la spec exige mantener como máximo dos.
- **Fix mínimo:** Antes de persistir, comprobar si la reasignación reactiva la fuente y excede el cupo; exigir y aplicar el reemplazo atómicamente como en la liberación. El contrato RPC también debe soportar esa decisión antes de permitir el envío.

#### [GOA1-07] La lista y el detalle recomiendan importes mensuales distintos
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/goals/presentation/GoalListScreen.kt:491-501; app/src/main/java/com/kipu/app/feature/goals/presentation/GoalContributionChartData.kt:32-38
- **Qué está mal:** La lista divide por la diferencia entre meses; el detalle incluye además el mes actual. La lista también recomienda para fechas vencidas, mientras el detalle las oculta.
- **Escenario de fallo:** En noviembre quedan S/300 para enero: la lista recomienda S/150 mensuales y el detalle S/100 durante noviembre, diciembre y enero.
- **Fix mínimo:** Reutilizar goalMonthlySuggestion en la tarjeta, incluida la regla para fechas vencidas. El test existente comprueba el cálculo del detalle, pero no su uso por la lista.

#### [GOA1-08] Las pruebas de gestión esperan controles retirados
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/goals/presentation/GoalManagementFlowTest.kt:42-50,89-121,285-310; app/src/main/java/com/kipu/app/feature/goals/presentation/GoalPlanLimitSheet.kt:32-35
- **Qué está mal:** Pruebas activas buscan Ver mis metas activas, 2 de 2 activas y Eliminar meta vacía. La hoja actual usa Entendido; la lista ya no muestra esos contadores ni ese texto de eliminación. La expectativa de un botón crear con metas existentes también quedó obsoleta.
- **Escenario de fallo:** rootCreateRequestHonoursGoalQuotaAndCanBeDismissedWithoutCreating intenta pulsar Ver mis metas activas, un control inexistente, y no alcanza su aserción de que no se creó una meta. Es una incompatibilidad estática comprobada, no un resultado de ejecución.
- **Fix mínimo:** Actualizar los selectores a los controles actuales sin quitar las aserciones de comportamiento. Test mínimo: abrir la hoja al cupo, pulsar Entendido y verificar cero llamadas de creación; validar el cupo real con repositorio, no solo con un estado de UI fabricado.

#### [GOA1-12] Los selectores exponen saldos aunque estén ocultos
- **Severidad:** media · **Categoría:** seguridad · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/goals/presentation/GoalFormScreen.kt:353-355,463-466,575-576; app/src/main/java/com/kipu/app/feature/goals/presentation/GoalContributionSheet.kt:292,321-326; app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailScreen.kt:398
- **Qué está mal:** Varias etiquetas convierten saldos reales/libres en texto directamente y no consultan LocalBalanceMasked. Algunos de esos importes también se incorporan al contentDescription del selector.
- **Escenario de fallo:** Con la opción de ocultar saldos activa, se abre Vincular cuenta en el formulario o Cuenta origen al confirmar un traslado. Los demás MoneyText muestran puntos, pero estas etiquetas muestran y anuncian el saldo exacto de la cuenta.
- **Fix mínimo:** Renderizar esos importes con MoneyText o su formateo enmascarado compartido y excluir el valor crudo de la semántica cuando se oculta. Test mínimo: proporcionar LocalBalanceMasked=true y comprobar que el saldo no aparece en texto ni descripciones.

#### [GOA1-13] El historial compone todos los eventos al abrir el detalle
- **Severidad:** media · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/goals/presentation/GoalEventHistory.kt:32-62; app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailScreen.kt:199-200,248-249,363-366
- **Qué está mal:** El historial usa Column con forEach dentro de un scroll que compone todas las filas, incluidas las que están fuera de pantalla. Se inserta completo en el detalle y de nuevo en la hoja de historial si se abre.
- **Escenario de fallo:** Una meta de larga duración acumula 3.000 aportes/liberaciones. Entrar al detalle crea miles de filas de texto, fechas e importes antes de poder recorrerlas; abrir el historial conserva esa composición de fondo y crea otra completa. El coste crece con todo el historial y no con las filas visibles.
- **Fix mínimo:** Usar una lista perezosa como contenedor del detalle y colocar allí las filas de eventos, o mostrar un resumen acotado y cargar la historia completa solo en una LazyColumn independiente. No anidar una LazyColumn sin límite dentro del scroll actual.

#### [GOA1-14] El test de recibos inválidos no alcanza la validación de estados
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/goals/data/GoalReplacementRoomIntegrationTest.kt:369-400; app/src/main/java/com/kipu/app/feature/goals/data/local/GoalLocalDataSource.kt:145-146,176-181
- **Qué está mal:** Las tres respuestas del test usan source/replacement, pero las metas creadas se llaman source-0/replacement-0 y así sucesivamente. Todos los casos se rechazan antes por IDs distintos; el caso NOT_A_STATUS nunca comprueba la validación del status ni el rollback tras actualizar la primera meta.
- **Escenario de fallo:** Se elimina por error la validación de validStatus en finishRemoteCommand: el test sigue pasando, porque su caso supuestamente dirigido al estado inválido retorna false al comparar los conjuntos de IDs.
- **Fix mínimo:** Construir cada respuesta con los IDs del caso correspondiente. Test mínimo: dos IDs correctos, primera meta con resultado válido y segunda con NOT_A_STATUS; comprobar que no cambia ninguna meta, recibo ni comando.

### Presupuestos

Porciones: BUD1 (Claude sonnet medium (relevo)). Hallazgos: 16 (2 alta, 5 media, 9 baja).

#### [BUD1-01] Un cambio pendiente de calendario/alcance se pierde cuando el presupuesto es de rango personalizado
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/budgets/domain/RenewBudgetPeriods.kt:23-37 (con BudgetLifecycleUseCases.kt:63-69)
- **Qué está mal:** `EditBudget` guarda un `PendingBudgetChange` con `effectiveFrom = fin del periodo actual`. En un CUSTOM_RANGE ese fin es justo el día siguiente a `end`. `RenewBudgetPeriods` comprueba primero "rango terminado" (`today >= end+1`) y finaliza el presupuesto con `pendingChange = null` antes de mirar el pendiente.
- **Escenario de fallo:** rango 1-30 nov; el 10 nov el usuario cambia el calendario a Mensual (o a otro rango 1-31 dic). El formulario avisa "aplican desde el siguiente periodo". El 1 dic el mantenimiento (o la lista) llama a `renew`, lo marca FINISHED, borra el pendiente y no crea periodo nuevo. El presupuesto muere en silencio, deja de alertar y el cambio nunca se aplica.
- **Fix mínimo:** en `RenewBudgetPeriods`, saltar el bloque de "fin de rango" si `budget.pendingChange?.schedule != null` y `!pending.effectiveFrom.isAfter(today)`; dejar que el bucle de abajo aplique el pendiente (ya sabe crear el primer periodo del nuevo calendario). Añadir el test que falta.

#### [BUD1-02] Un comando FAILED del outbox no se reintenta nunca y deja el presupuesto congelado para siempre
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/budgets/data/sync/SyncBudgetCommandsWorker.kt:73-91; app/src/main/java/com/kipu/app/feature/budgets/data/sync/BudgetHydration.kt:49-51,54,66; app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetListViewModel.kt:126-133
- **Qué está mal:** `REVISION_CONFLICT`, `BUDGET_QUOTA_EXCEEDED`, `INVALID_CATEGORY`, `HTTP_4xx` pasan a FAILED sin camino de salida (ni reintento, ni descarte, ni botón). Mientras exista un FAILED de ese presupuesto: (a) todos sus comandos nuevos se marcan `PARENT_COMMAND_FAILED` en la siguiente pasada (línea 75-78), (b) la hidratación lo salta (`blockedBudgetIds`, líneas 49-54 y 66), así que nunca vuelve a recibir lo del servidor. Solo `DELETE_BUDGET` pasa. La UI solo muestra un texto ("necesita revisión") sin acción.
- **Escenario de fallo:** el usuario Free crea un 3.º presupuesto en el móvil B mientras el móvil A ya tiene 2 activos (o edita el mismo presupuesto offline en A y B). B recibe 409/`BUDGET_QUOTA_EXCEEDED`, queda FAILED. Después finaliza uno de A (cupo libre) o resuelve el conflicto: B sigue sin sincronizar, no recibe cambios de A, y todo lo que edite en B se queda solo en ese teléfono. Si B se reinstala o se pierde, esos datos desaparecen.
- **Fix mínimo:** al inicio de cada `doWork`, pasar a PENDING los FAILED cuyo código sea reintentable (`BUDGET_QUOTA_EXCEEDED`, `REVISION_CONFLICT`, `PARENT_COMMAND_FAILED`) con un tope de intentos (usa `attemptCount`, que ya existe), y en `REVISION_CONFLICT` hacer que gane el servidor (hidratar y descartar el comando). Mínimo absoluto: una acción "descartar cambios locales" que borre los FAILED y deje hidratar.

#### [BUD1-03] Editar solo el calendario (o solo el alcance) borra el cambio pendiente anterior, y no hay forma de cancelarlo
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetLifecycleUseCases.kt:52-53,69; app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetFormViewModel.kt:177-192; app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetFormInput.kt:32-45
- **Qué está mal:** el formulario se rellena con el alcance/calendario ACTUAL (no el pendiente) y `buildEdit` solo envía lo que difiere de ese original. `EditBudget` construye un `PendingBudgetChange(scopeChange, scheduleChange, …)` nuevo que sustituye entero al anterior. El campo no tocado llega como null.
- **Escenario de fallo:** el 10 oct el usuario cambia el alcance Total → Comida (pendiente, entra el 1 nov). El 12 oct entra de nuevo y cambia solo el periodo Mensual → Quincenal: el nuevo pendiente trae `scope = null`, el cambio a Comida desaparece sin aviso. Inverso: para cancelar el pendiente de alcance, volver a elegir el alcance actual no cambia nada (`scopeChange` sale null y el pendiente se conserva).
- **Fix mínimo:** al armar `PendingBudgetChange`, mezclar con el pendiente existente (`scope = scopeChange ?: budget.pendingChange?.scope`, igual con `schedule`), y precargar el formulario con el valor pendiente cuando exista (para poder verlo y cancelarlo).

#### [BUD1-04] Una alerta puede perderse para siempre entre `tryRecord` y `notify`
- **Severidad:** media · **Categoría:** datos · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetAlerts.kt:35-37; app/src/main/java/com/kipu/app/feature/budgets/data/sync/BudgetMaintenanceWorker.kt:43-45
- **Qué está mal:** primero se graba la clave en el ledger y luego se notifica. Si entre ambos pasos el worker se cancela (o el proceso muere), el ledger dice "emitida" pero no hay aviso, y no se reintenta. La cancelación es fácil: cada gasto llama a `scheduleAfterConsumptionChange` con `ExistingWorkPolicy.REPLACE`, que cancela el worker en marcha.
- **Escenario de fallo:** gasto A cruza el 80%; el worker graba la clave y entra en `notify` (inserción Room suspendible); el usuario registra el gasto B un segundo después, REPLACE cancela el worker en ese punto. El aviso del 80% no existe y la siguiente evaluación ve el umbral como ya emitido.
- **Fix mínimo:** invertir el orden: `notifier.notify(alert)` primero y `ledger.tryRecord(key)` después. `BudgetCenterAlertNotifier` ya es idempotente por `dedupeKey` (`insertIfAbsent`), así que repetir no duplica ni el aviso ni la notificación del sistema.

#### [BUD1-05] La tarjeta "Presupuesto total / Usado" suma presupuestos que se solapan
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetListScreen.kt:371-393
- **Qué está mal:** suma `limitMinor` y `consumedMinor` de todos los presupuestos activos de la misma moneda. Un presupuesto Total y uno de categoría (o categoría padre + subcategoría) miden el mismo gasto, que se cuenta dos veces.
- **Escenario de fallo:** Total S/ 1000 y Alimentación S/ 300 en soles; el usuario gastó S/ 200 en comida y S/ 0 en lo demás. La tarjeta muestra "Presupuesto total S/ 1300" y "Usado S/ 400", cuando el gasto real es 200 y el tope global es 1000.
- **Fix mínimo:** calcular el resumen solo con presupuestos de alcance Total (si hay) y, si no, con los de categoría de nivel raíz sin solape; o quitar la tarjeta. Es decisión de producto (ver Dudas).

#### [BUD1-06] El detalle calcula el consumo de TODOS los periodos históricos aunque solo muestra 3
- **Severidad:** media · **Categoría:** rendimiento · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/budgets/presentation/BudgetDetailViewModel.kt:160-170 (con BudgetConsumptionUseCases.kt:61-64 y RoomBudgetCategoryLookup.kt:14-32)
- **Qué está mal:** `build()` hace `periods.map { getConsumption.forPeriod(...) }` secuencial, y cada llamada lanza una consulta de gasto y recarga todas las categorías del usuario. El flujo se reconstruye con cada emisión de `observeBudgets/observePeriods/categorías`, incluidas las que provoca la hidratación (BUD1-07). La UI solo pinta 3 filas hasta pulsar "Ver todos".
- **Escenario de fallo:** presupuesto diario con 1 año de historia = 365 consultas + 365 lecturas de categorías cada vez que se abre el detalle o llega una hidratación; la pantalla queda en "Cargando" varios segundos en un gama baja.
- **Fix mínimo:** calcular consumo solo para el periodo actual y los 3 más recientes; el resto, al pulsar "Ver todos" (o al hacer scroll).

#### [BUD1-12] Faltan tests de los caminos de error del sync y de la hidratación con outbox bloqueado
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/budgets/data/sync/BudgetTombstoneReplayIntegrationTest.kt (cubre cuota y replay de lápida, nada más); app/src/test/.../BudgetSyncRescueTest.kt (solo el rescate del scheduler)
- **Qué está mal:** `SyncBudgetCommandsWorker` (409 → `confirmBudget/confirmPeriod`, `REVISION_CONFLICT`, `PARENT_COMMAND_FAILED`, RETRY con `attemptCount`, DELETE de presupuesto nunca sincronizado) y `BudgetHydration` (saltar presupuestos bloqueados, `strict` vs no) no tienen un test que falle si se rompen. Es donde están BUD1-02 y BUD1-07.
- **Escenario de fallo:** un cambio que haga que 409 no compare el estado remoto provocaría duplicados o ediciones perdidas sin que ningún test lo detecte.
- **Fix mínimo:** un test JVM con `MockEngine` (ya usado en androidTest) por caso: (1) 409 con estado igual → APPLIED, (2) 409 con estado distinto → FAILED/REVISION_CONFLICT y el siguiente comando del mismo presupuesto → `PARENT_COMMAND_FAILED`, (3) hidratación no pisa un presupuesto con comando PENDING.

#### [BUD1-07] Cada foreground re-descarga y re-escribe todo (todos los periodos y todas las lápidas)
- **Severidad:** baja · **Categoría:** escala · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/budgets/data/remote/BudgetApi.kt:40-41,71-85; app/src/main/java/com/kipu/app/feature/budgets/data/sync/BudgetHydration.kt:60-62,71; app/src/main/java/com/kipu/app/feature/budgets/data/sync/SyncBudgetCommandsWorker.kt:160-165,167-184
- **Qué está mal:** `MainActivity.onResume` encola el sync en cada vuelta a primer plano (cross-módulo SEC1, línea 180-181). El worker baja SIEMPRE todas las lápidas (sin paginar ni incremental), todos los presupuestos y todos los periodos, y hace `REPLACE` incondicional de cada fila (aunque no cambie nada), lo que dispara las invalidaciones de Room y la reconstrucción de listas (BUD1-06). Además, `confirmPeriod` tras un 409 baja TODOS los periodos para comparar uno.
- **Escenario de fallo:** 2 presupuestos diarios tras 2 años (~1500 periodos) = 3 páginas de 500 en cada resume, más 1500 REPLACE y recálculo de consumo del detalle abierto; las lápidas crecen sin tope (se borran presupuestos a lo largo de los años y se vuelven a bajar todas, con el límite de 1000 filas por defecto de PostgREST sin paginar).
- **Fix mínimo:** hidratar con filtro `updated_at=gt.<último>` (budgets) y solo periodos de presupuestos cambiados; comparar antes de escribir (`if (local != remote)`); `confirmPeriod` con `id=eq.` en vez de `fetchPeriods`.
- **Cross-módulo:** parcial → SEC1 por el disparo en `MainActivity.onResume`

#### [BUD1-08] El outbox nunca se poda y se lee entero en cada sync
- **Severidad:** baja · **Categoría:** escala · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/budgets/data/local/BudgetDao.kt:77-78; app/src/main/java/com/kipu/app/feature/budgets/data/sync/SyncBudgetCommandsWorker.kt:73; app/src/main/java/com/kipu/app/feature/budgets/data/sync/BudgetHydration.kt:49
- **Qué está mal:** los comandos COMPLETED no se borran jamás y `allOutbox(userId)` carga todas las filas (con `payload_json`) solo para saber qué presupuestos tienen FAILED/PENDING. Los diarios añaden una fila por día (más una por cada cambio de límite).
- **Escenario de fallo:** tras 2 años con presupuestos diarios hay miles de filas COMPLETED; cada sync y cada hidratación las lee por completo en memoria.
- **Fix mínimo:** `DELETE FROM budget_sync_outbox WHERE state='COMPLETED' AND updated_at < :cutoff` al final de un sync correcto y reemplazar `allOutbox` por una consulta `SELECT DISTINCT aggregate_id ... WHERE state IN ('PENDING','FAILED')`.

#### [BUD1-09] `EditBudget` sigue editando un presupuesto que su propia renovación acaba de finalizar
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetLifecycleUseCases.kt:33-34,46-47,70-83
- **Qué está mal:** comprueba `isActive` antes de llamar a `renew`, y `renew` puede finalizar un rango vencido; después relee el presupuesto (ya FINISHED) y sigue, creando un periodo nuevo en un presupuesto FINISHED (no alerta, no se renueva). El test `endedCustomRangeAppliesChangeImmediately` (BudgetLifecycleTest.kt:80-87) pasa porque no comprueba el estado final.
- **Escenario de fallo:** el formulario de edición quedó abierto desde antes de la medianoche del último día del rango; al guardar después de las 00:00 el usuario cree que "extendió" el presupuesto, pero queda FINISHED con un periodo huérfano. En producción es estrecho (la lista renueva antes y el formulario rechaza FINISHED), por eso baja.
- **Fix mínimo:** tras la relectura, `if (!budget.isActive) return Rejected(BudgetFinished)`.

#### [BUD1-10] Se acepta un rango personalizado ya pasado y se finaliza en cuanto se muestra la lista
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetValidator.kt:40-45; app/src/main/java/com/kipu/app/feature/budgets/domain/CreateBudget.kt:57
- **Qué está mal:** `validateSchedule` solo exige `end >= start`. `CreateBudget` crea el periodo del rango aunque ya haya terminado.
- **Escenario de fallo:** el 20 oct el usuario crea "Viaje" del 1 al 10 oct. Pantalla "Presupuesto creado" y, al volver a la lista, `renew` lo pasa a FINISHED y consume un ciclo de sync/cupo del servidor (el INSERT remoto cuenta como activo un momento) para nada.
- **Fix mínimo:** en `CreateBudget` rechazar `CustomRange` con `end < today` (nuevo `BudgetError`), o permitirlo y documentarlo; hoy es implícito.

#### [BUD1-11] `scheduleSync` usa REPLACE: cada llamada cancela el sync en marcha
- **Severidad:** baja · **Categoría:** concurrencia · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/budgets/data/sync/SyncBudgetCommandsWorker.kt:227-231
- **Qué está mal:** con `ExistingWorkPolicy.REPLACE`, cada escritura de presupuesto y cada `onResume` aborta el worker que está empujando comandos, que vuelve a empezar y rehace tombstones + fetch. Es seguro (todo es idempotente) pero no progresa si el usuario entra y sale de la app repetidamente o edita ráfagas.
- **Escenario de fallo:** con red lenta, 3 ediciones seguidas reinician 3 veces el worker sin que llegue a hidratar ni a marcar COMPLETED lo ya enviado, y se repiten los POST de periodos.
- **Fix mínimo:** `ExistingWorkPolicy.APPEND_OR_REPLACE` (o KEEP + volver a encolar al final si quedó PENDING).
- **Cross-módulo:** parcial → SEC1 (`MainActivity.onResume` dispara el sync)

#### [BUD1-13] Los fakes de test difieren del repositorio real y están duplicados; un test de otra feature vive aquí
- **Severidad:** baja · **Categoría:** fusionar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/feature/budgets/domain/BudgetFakes.kt:31-90 y app/src/test/java/com/kipu/app/feature/budgets/presentation/BudgetTestFakes.kt:45-105; app/src/test/java/com/kipu/app/feature/budgets/BudgetIdeasTest.kt:42-49
- **Qué está mal:** hay dos `FakeBudgetRepository`, `FakePlanAccess` y `FakeCategoryLookup/FakeCategories` casi idénticos. Ninguno reproduce lo que hace el repositorio real: `update` real exige `budget.revision > expected`, comprueba lápida y actualiza el límite del periodo activo; los fakes no, y por eso los tests de dominio dependen de que `EditBudget` llame a `updatePeriodLimit` (que en producción es redundante, BUD1-16). `BudgetIdeasTest` incluye 5 aserciones de `GoalIdea` (feature goals, GOA1).
- **Escenario de fallo:** un cambio en `OfflineFirstBudgetRepository.update` (p. ej. regresión en la actualización del periodo activo) no rompe ningún test JVM; los de dominio pasan porque el fake hace otra cosa.
- **Fix mínimo:** un solo juego de fakes en un archivo común (`testFixtures` o el paquete `domain`) y mover las aserciones de `GoalIdea` a su test de goals. Los casos de `update` ya están cubiertos de verdad en `OfflineFirstBudgetRepositoryRoomTest` (androidTest); añadir ahí el de "cambio de límite actualiza periodo activo".
- **Cross-módulo:** GOA1 solo por las aserciones de `GoalIdea`
- **Ahorro estimado:** ~110 líneas

#### [BUD1-14] Código muerto
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/budgets/data/local/BudgetDao.kt:50-51 (`updatePeriod`), :62-63 (`tombstonedBudgetIds`), :101-102 (`countForBudget`, solo lo usa un androidTest); app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetModels.kt:91 (`isClosedOn`); app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetPeriodCalculator.kt:49-58 (`missingPeriods`, solo test; `RenewBudgetPeriods` tiene su propio bucle)
- **Qué está mal:** grep en main, test y androidTest: sin llamadas (salvo las indicadas). `BudgetCommandResponseDto.budgetId/deleted` también se decodifican y no se leen.
- **Escenario de fallo:** no es un fallo, es mantenimiento: `missingPeriods` y el bucle de `RenewBudgetPeriods` pueden divergir y el test de `missingPeriods` da falsa confianza sobre la renovación real.
- **Fix mínimo:** borrar esos miembros y el test `missingPeriodsFillsGapWithoutDuplicates`; dejar `countForBudget` solo si se quiere mantener el androidTest.
- **Ahorro estimado:** ~22 líneas

#### [BUD1-15] Constantes, helper y JSON duplicados entre repositorio, worker e hidratación
- **Severidad:** baja · **Categoría:** fusionar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/budgets/data/OfflineFirstBudgetRepository.kt:254-256 vs data/sync/SyncBudgetCommandsWorker.kt:208-210; BudgetLocalMappings.kt:107 vs BudgetRemoteMappings.kt:22; `isCurrentOwner` en OfflineFirstBudgetRepository.kt:239, RoomBudgetAlertLedger.kt:37 y BudgetCenterAlertNotifier.kt:24; SyncBudgetCommandsWorker.kt:187
- **Qué está mal:** los nombres de comando (`UPSERT_BUDGET`, …) están escritos dos veces como strings privados; si uno cambia, el worker deja de reconocerlos y marca `INVALID_LOCAL_COMMAND`. La CSV de umbrales se parsea en dos sitios. `BudgetHydration` es `@Inject` pero el worker lo construye a mano con `BudgetHydration(...)`.
- **Escenario de fallo:** renombrar una constante en el repositorio deja comandos nuevos fallando con `INVALID_LOCAL_COMMAND` sin error de compilación.
- **Fix mínimo:** el worker usa `OfflineFirstBudgetRepository.UPSERT_*`; inyectar `BudgetHydration` en el worker; un único `csvInts` interno.
- **Ahorro estimado:** ~12 líneas

#### [BUD1-16] `EditBudget` aplica dos veces el límite al periodo activo (y en dos transacciones distintas)
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetLifecycleUseCases.kt:93-96 vs app/src/main/java/com/kipu/app/feature/budgets/data/OfflineFirstBudgetRepository.kt:104-115
- **Qué está mal:** `OfflineFirstBudgetRepository.update` ya actualiza el periodo activo cuando cambia el límite, dentro de la misma transacción (con otra definición de "hoy": reloj+zona del repositorio, no `c.today`). Luego `EditBudget` llama a `updatePeriodLimit` con el objeto `currentPeriod` leído antes, y el repositorio responde OK sin hacer nada (`period.limitMinor == limitMinor`). Es una segunda versión de la misma regla.
- **Escenario de fallo:** si algún día `c.today` y el reloj del repositorio difieren (p. ej. cambio de zona entre la lectura y el guardado), cada camino elegirá un periodo distinto y habrá dos periodos con límites distintos.
- **Fix mínimo:** borrar las líneas 93-96 del caso de uso y hacer que el fake de test reproduzca el comportamiento del repositorio (ver BUD1-13).
- **Ahorro estimado:** ~4 líneas

### Recurrencias y calendario

Porciones: REC1 (Claude sonnet medium (relevo)). Hallazgos: 16 (2 alta, 8 media, 6 baja).

#### [REC1-01] Una regla en CONFLICT o FAILED queda bloqueada para siempre
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:244 (applyChangeFeed), :140-176 (setStatus), feature/recurrences/data/local/RecurrenceOutboxDao.kt:24-36 (getReady), feature/recurrences/presentation/RecurrenceLifecycleScreen.kt:65-73
- **Qué está mal:** la regla pasa a CONFLICT/FAILED (comando rechazado, 409, o servidor por delante) y no existe ninguna salida. El feed remoto no la pisa porque `applyChangeFeed` ignora filas con `syncState in (CONFLICT, FAILED)`. Los comandos nuevos se encolan pero `getReady` no envía ninguno mientras exista uno anterior de la misma regla que no esté APPLIED/DUPLICATE. La UI sigue mostrando Pausar/Reanudar/Cancelar y el texto "vuelve a intentarlo", pero no hay ningún reintento. `setStatus` tampoco mira `syncState`.
- **Escenario de fallo:** el usuario tiene 2 dispositivos. A pausa la regla (rev 2 aplicada en servidor). B estaba offline en rev 1 y cancela; al reconectar envía `expected_revision=1`, el servidor responde REVISION_MISMATCH y B queda en CONFLICT. B nunca recibe la rev 2 del servidor. Si luego B pulsa "Cancelar" o "Reanudar", el cambio queda solo en B (el comando nuevo está bloqueado detrás del conflictivo) y A sigue con la regla activa. Además `reconcileReminder` exige `syncState == SYNCED`, así que B deja de recordar esa regla.
- **Fix mínimo:** en `applyChangeFeed` aceptar el remoto cuando `current.syncState` es CONFLICT/FAILED y `remote.revision >= current.revision` (descartando de paso los comandos CONFLICT/FAILED de esa regla), es decir, "gana el servidor" y se vuelve a SYNCED. Alternativa mínima: ocultar los botones y mostrar un botón "Usar la versión del servidor" que haga lo mismo.

#### [REC1-02] El recordatorio de una regla solo se dispara una vez en su vida
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:323-341 (reconcileReminder), feature/recurrences/data/sync/RecurrenceReminderWorker.kt:54-58, feature/recurrences/data/sync/RecurrenceReminderScheduler.kt:54-70
- **Qué está mal:** nada avanza `next_due_date` (se grepeó app y `supabase/migrations`: solo lo escriben create, update y la reanudación). El recordatorio se programa solo para `nextDueDate`, y el worker se descarta si `recurrence.nextDueDate != dueDate`. Cuando pasa esa fecha no se programa la siguiente ocurrencia. En cambio `GenerateExpectedPayments` sí calcula la ocurrencia futura con `firstOccurrenceAfter`, y el calendario también proyecta ocurrencias posteriores. Es decir, el calendario y los pagos esperados muestran el mes siguiente pero no hay aviso.
- **Escenario de fallo:** regla mensual creada el 1-nov con `nextDueDate=2026-11-01` y aviso de 2 días. El 30-oct llega el aviso. El 1-dic, 1-ene, etc. no llega ninguno, aunque el calendario y el pago esperado sí muestran esas fechas. La app no tiene pantalla de editar (ver REC1-15), así que el usuario solo puede reiniciar el ciclo con pausar + reanudar.
- **Fix mínimo:** en `reconcileReminder` usar `firstOccurrenceAfter(start, freq, interval, unit, hoy_Lima - 1)` (igual que `GenerateExpectedPayments`), validar en el worker con `isOccurrence` en vez de igualdad con `nextDueDate`, y que el worker reprograme la siguiente ocurrencia al terminar. Si la intención de producto es otra, ver Dudas.

#### [REC1-03] Leer la lista de recurrencias reinicia el trabajo de sincronización
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:57-60 y :257-265, feature/recurrences/data/sync/RecurrencesSyncScheduler.kt:37-42
- **Qué está mal:** `observeAll()` es una lectura pero llama a `scheduleSync`, que hace `enqueueUniqueWork(..., ExistingWorkPolicy.REPLACE)`. REPLACE cancela el worker que esté corriendo y lo vuelve a encolar. Se invoca desde `ObserveCalendarMonth` (cada vez que se abre o se cambia de mes), el ViewModel de recurrencias y `ExpectedPaymentWorker` (cada 6 h).
- **Escenario de fallo:** hay 1 comando pendiente y el usuario abre el calendario y pasa 5 meses seguidos. Cada cambio de mes cancela el worker a mitad de la llamada HTTP y lo reinicia. El comando queda LEASED 60 s sin recibo (se reenvía luego, el servidor responde DUPLICATE, así que no se pierden datos), pero la sincronización se retrasa y se multiplican las llamadas.
- **Fix mínimo:** quitar `scheduleSync` de `observeAll` (ya se llama tras cada escritura y existe el periodic de rescate) o usar `ExistingWorkPolicy.KEEP`.

#### [REC1-04] Pagos esperados de reglas pausadas, canceladas o editadas siguen casando con capturas
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** feature/recurrences/domain/ExpectedPayments.kt:24-36, feature/recurrences/data/local/ExpectedPaymentEntity.kt:56-67 y :85-87
- **Qué está mal:** `GenerateExpectedPayments` solo inserta filas EXPECTED; si la regla no está ACTIVE solo sincroniza importe/moneda y sale. Nunca marca SKIPPED (el estado existe pero ningún código lo escribe) ni borra filas viejas. `findExpectedByAmount` filtra por status, importe, moneda y ±3 días, sin mirar el estado de la regla.
- **Escenario de fallo:** (a) regla de S/ 100 con EXPECTED el día 12; el usuario la pausa el día 10; el día 11 llega una captura de S/ 100 sin relación y `MatchCapturedMovementToExpected` la enlaza a esa regla pausada. (b) Se cambia `nextDueDate` de 15 a 20 (por pausa + reanudar): quedan dos filas EXPECTED, y un pago del 18 casa con ambas y fuerza un PENDING_CHOICE innecesario.
- **Fix mínimo:** en `GenerateExpectedPayments`, antes de insertar, un `UPDATE ... SET status='SKIPPED' WHERE recurrence_id=:id AND status='EXPECTED' AND (:inactive OR due_date <> :due)` (una query en el DAO) y que el generador se invoque también con la regla pausada o cancelada.
- **Cross-módulo:** parcial (el matching vive en capture → CAP1; el origen de las filas es de esta porción)

#### [REC1-05] `isOccurrence` falla para anuales ancladas al 29 de febrero
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** feature/recurrences/domain/schedule/RecurrenceScheduleCalculator.kt:31-42 y :58-66 (línea 64, `ChronoUnit.YEARS.between`)
- **Qué está mal:** `estimateIndex` usa `ChronoUnit.YEARS.between(2024-02-29, 2026-02-28)` = 1, pero la ocurrencia del 28-feb-2026 es la de índice 2 (se recorta el día). `isOccurrence` solo mira desde el índice estimado hacia atrás y devuelve false. El test existente cubre `occurrenceAt` para este caso, pero no `isOccurrence`.
- **Escenario de fallo:** regla ANUAL con `startDate=2024-02-29`. En febrero de 2027 el calendario no muestra el 28-feb (`fromRecurrences` usa `isOccurrence`). Además `create`/`update` rechazan ese 28-feb como "Next due date is not on the schedule" aunque `firstOccurrenceAfter` y la reanudación sí la consideran válida. El servidor (`_rec_schedule_valid`) no se revisó en esta porción.
- **Fix mínimo:** en `estimateIndex` para Years, `(date.year - anchor.year) / amount` (sin la regla de día) y dejar que el bucle de `isOccurrence` corrija; añadir también el paso hacia adelante (un `index+1` de comprobación).

#### [REC1-06] `update` y `setStatus` no protegen el trabajo posterior al commit
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:127-133 y :176-185 (comparar con :83-89 en create)
- **Qué está mal:** `create` envuelve `reconcileReminder` y `generateExpected` en try/catch porque la regla ya está guardada (hay un doc QA que lo menciona). `setStatus` y `update` no. Si lanzan después del commit, la excepción sube y el ViewModel muestra "No se pudo pausar..." con la regla ya cambiada. Si lo que lanza es una `IllegalArgumentException` (por ejemplo `firstOccurrenceAfter` por desbordar fechas), el `catch` final la convierte en `Rejected("VALIDATION_FAILED")`.
- **Escenario de fallo:** el usuario pulsa Pausar, la fila ya está PAUSED con su comando en el outbox, pero `reminderScheduler.cancel` o WorkManager lanzan una excepción. La pantalla dice que falló; el usuario reintenta y recibe "Conflicto de revisión: se esperaba N, versión actual N+1".
- **Fix mínimo:** mover `reconcileReminder` + `generateExpected` de ambos métodos al mismo helper protegido que usa `create`.

#### [REC1-07] El mismo recordatorio puede notificarse varias veces
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** feature/recurrences/data/sync/RecurrenceReminderScheduler.kt:57-70, feature/recurrences/data/sync/RecurrenceReminderWorker.kt:93-106, feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:192-231 y :254
- **Qué está mal:** cada `reconcileReminder` re-encola con REPLACE. Si la hora de disparo (fecha - lead, 00:00 Lima) ya pasó, el delay es 0 y el worker corre de inmediato. El worker no comprueba si ya notificó esa fecha (`markDelivered` solo escribe) y publica con prioridad DEFAULT sin `setOnlyAlertOnce`. Se reconcilia tras cada recibo y por cada cambio del feed (incluida una edición hecha en el otro dispositivo).
- **Escenario de fallo:** aviso de 3 días, vencimiento el día 20. El día 17 ya sonó. El día 18 el usuario pausa y reanuda en su otro teléfono; este dispositivo recibe el cambio del feed y vuelve a mostrar y sonar la notificación aunque el usuario ya la había descartado. El test androidTest solo comprueba que quede 1 notificación activa, no que no vuelva a sonar.
- **Fix mínimo:** en el worker, saltar si el `LocalReminderEventEntity` de ese `id` determinista ya existe, o en el scheduler usar KEEP cuando la fecha y el lead no cambiaron.
- **Cross-módulo:** parcial (tablas de eventos de notificación en UI3)

#### [REC1-08] Una página de feed "rara" bloquea la sincronización de recurrencias para siempre
- **Severidad:** media · **Categoría:** escala · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** feature/recurrences/data/sync/RecurrenceHydration.kt:24-43 y :45-66 (`require(operation == "UPSERT")`, `RecurrenceStatus.valueOf`)
- **Qué está mal:** cualquier `IllegalArgumentException` al convertir un cambio (operación distinta de UPSERT, estado nuevo en el enum, campo ausente) escapa de `pullChanges`, el worker hace `Result.retry()` y el checkpoint no avanza, así que se reintenta la misma página eternamente (backoff, pero sin límite).
- **Escenario de fallo:** se despliega una versión de servidor que añade un estado o una operación DELETE para recurrencias. Todos los clientes con la versión anterior dejan de sincronizar recurrencias (la fase de envío de comandos va antes del pull, pero el worker nunca llega a `Success`).
- **Fix mínimo:** tratar el cambio ilegible como "saltar y avanzar el checkpoint" (log con SecureLog), o convertir la excepción en `false` sin reintento infinito.

#### [REC1-12] Un test instrumentado aserta un texto que la pantalla ya no muestra
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/recurrences/RecurrenceLifecycleFlowTest.kt:59-65
- **Qué está mal:** `staleOfflineConflictAndRevisionAreVisible` busca `"Estado: ACTIVE · Próxima fecha: ... · Revisión 4"`. `RecurrenceLifecycleScreen` solo pinta `recurrenceStateLabel` ("Activa · próximo ..."); en `feature/recurrences` no existe la cadena "Estado:" ni "Revisión N" en la UI (grep). El test no puede pasar.
- **Escenario de fallo:** la próxima ejecución de androidTest falla en este caso (o se ignora y se pierde la confianza en la suite).
- **Fix mínimo:** cambiar la aserción al texto actual (`recurrenceStateLabel(...)`) o borrarla; el resto del test (tag de conflicto) sí es válido.

#### [REC1-14] Sin test para el feed que pisa una regla local (conflicto "servidor por delante")
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:233-255
- **Qué está mal:** `RecurrenceSyncTest` cubre crear, recibos y un feed solo para una regla nueva. Ninguna prueba cubre `applyChangeFeed` con una regla existente (remoto > local sin pendientes, remoto > local con pendientes, remoto con CONFLICT local), que es donde viven REC1-01 y REC1-02.
- **Escenario de fallo:** cualquier cambio en esa rama (p. ej. el arreglo de REC1-01) no tiene red de seguridad.
- **Fix mínimo:** un test: regla local rev 1 SYNCED, feed trae rev 2 → fila rev 2 SYNCED; mismo feed con un comando pendiente → CONFLICT/`REMOTE_REVISION_AHEAD`.

#### [REC1-09] El recordatorio se dispara a las 00:00 de Lima, con sonido
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** feature/recurrences/data/sync/RecurrenceReminderScheduler.kt:55-56 y feature/recurrences/data/sync/RecurrenceReminderWorker.kt:113-118
- **Qué está mal:** `fireAt = (dueDate - lead).atStartOfDay(Lima)`. El canal es IMPORTANCE_DEFAULT (suena). El test `reminderDelayUsesLimaMidnightAndInjectedClock` fija ese comportamiento como correcto.
- **Escenario de fallo:** aviso de 2 días para un vencimiento del 10: suena a las 00:00 del día 8.
- **Fix mínimo:** sumar una hora fija de aviso (p. ej. 09:00 Lima) a `fireAt`; ver Dudas por si es decisión de producto.

#### [REC1-10] El concepto con comillas se ve con barras en "Mis recurrencias"
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** feature/recurrences/presentation/RecurrenceLifecycleScreen.kt:59
- **Qué está mal:** `templateTransaction["concept"]?.toString()?.trim('"')` serializa el JSON (escapa `"` y `\`) en vez de leer el contenido.
- **Escenario de fallo:** concepto `Pago "Luz"` se muestra `Pago \"Luz\"`; uno que termine en comilla pierde ese carácter.
- **Fix mínimo:** `(rule.templateTransaction["concept"] as? JsonPrimitive)?.content ?: "Obligación"`.

#### [REC1-11] El calendario congela "hoy" y usa la zona del dispositivo; el resto usa Lima
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** feature/calendar/presentation/CalendarViewModel.kt:52-53, feature/calendar/domain/ObserveCalendarMonth.kt:39
- **Qué está mal:** `today` se calcula una vez al crear el ViewModel y la zona es `ZoneId.systemDefault()`. Pagos esperados, recordatorios y registro usan `LIMA_BUSINESS_ZONE`.
- **Escenario de fallo:** se abre el calendario a las 23:50 y la app queda en segundo plano; al día siguiente "hoy" y los estados "Vencido" siguen en el día anterior. Un usuario con el teléfono en otra zona (viaje) ve movimientos agrupados por otro día que los avisos.
- **Fix mínimo:** derivar `today` de un flujo con el reloj en cada resubscripción (`stateIn` con `WhileSubscribed` ya lo permite) y usar `LIMA_BUSINESS_ZONE` como en el resto.

#### [REC1-13] Test que verifica su propio fake
- **Severidad:** baja · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/feature/recurrences/data/RecurrenceDatesAndRemindersTest.kt:89-102
- **Qué está mal:** `freeCaptureGateDoesNotSuppressExpectedPaymentOrReminder` crea un `FakeCaptureAccessGate`, lo evalúa él mismo y asserta que devolvió lo que se le configuró, con `evaluations == 1` (la llamada que acaba de hacer). El gate no se pasa a `GenerateExpectedPayments` (que ni lo recibe) ni al worker; las otras aserciones ya existen en `serverConfirmedActiveRuleCreatesReminderEvent`.
- **Escenario de fallo:** si alguien añade el gate al camino de recordatorios, el test sigue verde.
- **Fix mínimo:** quitar las líneas del fake (91, 92, 101) o inyectar el gate en el worker y comprobar que sigue publicando.

#### [REC1-15] Código de edición de recurrencias sin ningún llamador
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:95-138 y :301-307; feature/recurrences/domain/RecurrenceRepository.kt:13 y :41-50; feature/recurrences/data/local/RecurrenceDao.kt:26-42 y :57-58 (`applyRemoteState`); feature/recurrences/domain/model/RecurrenceRule.kt:31 (`Synced`); feature/recurrences/data/sync/SyncRecurrenceCommandsWorker.kt:110 (`MAX_CHANGE_PAGES`)
- **Qué está mal:** `repository.update` y `RecurrenceUpdate` solo se usan en tests (grep en `app/src`); ningún ViewModel ni pantalla edita una regla. `applyRemoteState`, `RecurrenceCommandResult.Synced` y `MAX_CHANGE_PAGES` no tienen usos fuera de definiciones y un `when`. `ExpectedPaymentStatus.SKIPPED` tampoco se escribe nunca (ver REC1-04).
- **Escenario de fallo:** no rompe nada; son rutas sin cobertura de uso real que hay que mantener (la de `update` además duplica validaciones de `validateDraft`).
- **Fix mínimo:** borrar lo muerto, o conectar una pantalla de editar (que además resolvería la imposibilidad de mover `nextDueDate` de REC1-02). Decidir con producto antes de borrar `update`, porque el contrato servidor `UPDATE_RECURRENCE` existe.
- **Ahorro estimado:** ~85 líneas

#### [REC1-16] Rama de "deuda no disponible" inalcanzable desde el caso de uso
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** baja · **Estado:** probable (confianza baja del worker, no reabierto)
- **Ubicación:** feature/calendar/domain/CalendarEventProjector.kt:98-118
- **Qué está mal:** `ObserveCalendarMonth.debtsWithInstallments` solo observa cuotas de deudas ACTIVE que están en `debts`, así que `debtsById[debtId]` nunca es null en producción; solo el test unitario la ejercita.
- **Escenario de fallo:** ninguno; código y chips (`isSourceAvailable`, `UnavailableChip`, aviso del sheet) que nunca se ven.
- **Fix mínimo:** o se borra la rama, o se pasa también el mapa de cuotas de deudas no activas si el producto quiere ese caso.
- **Ahorro estimado:** ~35 líneas (con la UI asociada)

### Captura e importación

Porciones: CAP1 (Codex gpt-6.1-terra medium (2 pasadas)); CAP2 (Claude sonnet medium (relevo)). Hallazgos: 26 (5 alta, 12 media, 9 baja).

#### [CAP1-01] Una referencia repetida puede ocultar otra operación
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/capture/data/local/RoomCaptureCorrelation.kt:15-23; app/src/main/java/com/kipu/app/feature/capture/domain/CaptureCorrelation.kt:19-22; app/src/main/java/com/kipu/app/feature/capture/data/local/RoomCaptureStores.kt:45-57
- **Qué está mal:** La asociación automática compara referencia, monto, moneda y dirección, pero no exige fuentes distintas ni comprueba la fecha. Ante una coincidencia se descarta el nuevo candidato como duplicado sin pedir confirmación.
- **Escenario de fallo:** Dos avisos distintos del mismo banco llevan la referencia 123456, ambos son gastos de S/ 50 y corresponden a fechas diferentes: el segundo se adjunta al primero y no aparece como movimiento pendiente.
- **Fix mínimo:** Exigir fuentes complementarias y validar una coincidencia temporal razonable antes de unir automáticamente; si no hay certeza, conservar ambos candidatos para revisión. Añadir un test Room con dos avisos de la misma fuente y referencia repetida en fechas distintas.

#### [CAP1-02] Al descartar se conserva el texto extraído
- **Severidad:** alta · **Categoría:** seguridad · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/capture/data/local/CaptureDaos.kt:114-123; app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureComponents.kt:193
- **Qué está mal:** El diálogo promete borrar el texto detectado, pero el descarte solo borra `evidence_snippet`; deja `merchant_source_text`, extraído literalmente de la notificación. La purga de siete días tampoco elimina ese campo.
- **Escenario de fallo:** Una notificación «Yapeaste S/ 10 a Ana Pérez» crea el texto de contraparte «Ana Pérez»; después de descartarla y vencida la evidencia, ese nombre sigue guardado en la fila local descartada.
- **Fix mínimo:** Borrar `merchant_source_text` al descartar, y ajustar la retención de los demás fragmentos derivados conforme a la intención de producto. Añadir un test Room que descarte un candidato con nombre y compruebe el contenido real de la fila.

#### [CAP1-04] Elegir otra cuenta cambia la moneda sin convertir el monto
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureReviewViewModel.kt:395-400; app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureReviewViewModel.kt:498-499; app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureReviewScreen.kt:745
- **Qué está mal:** Al elegir una cuenta o tarjeta, la pantalla sustituye la moneda detectada por la de ese instrumento, pero deja intacto el monto. Si el aviso ya tenía moneda, no se ofrece el selector de moneda para corregirla independientemente.
- **Escenario de fallo:** Un aviso por S/ 120 se revisa seleccionando una cuenta en dólares: el formulario pasa a US$ 120 y, al confirmar, envía 12000 centavos USD, aunque el aviso era en soles.
- **Fix mínimo:** No cambiar silenciosamente la moneda detectada: impedir la selección incompatible o exigir una corrección explícita de monto y moneda antes de guardar. Añadir un test del ViewModel con candidato PEN y cuenta USD que compruebe que no se registra USD 120 sin intervención expresa.

#### [CAP1-06] Un fallo temporal antes de guardar pierde el aviso
- **Severidad:** alta · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/capture/data/listener/KipuNotificationListenerService.kt:35-56; app/src/main/java/com/kipu/app/feature/capture/domain/ExtractCaptureCandidate.kt:53-58,120-130
- **Qué está mal:** Se hacen consultas antes de persistir y el listener ignora sus excepciones. El worker solo reanuda candidatos ya guardados.
- **Escenario de fallo:** Si el resolutor de cuenta falla temporalmente y Android no vuelve a publicar el aviso, no se crea candidato y el movimiento no aparece en la bandeja.
- **Fix mínimo:** Conservar de forma segura un trabajo mínimo antes de esas consultas y reintentarlo respetando la sesión, el permiso y la retención; probar sin un segundo callback.
- **Verificación (Fable):** Confirmado: el listener captura cualquier excepción antes de persistir y no deja rastro; el worker solo reanuda candidatos ya guardados.

#### [CAP2-01] Al elegir tarjeta a mano en un estado Visa no se recalculan los duplicados
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/statements/data/local/RoomStatementImportStore.kt:78-103 (duplicados solo en 96-100); app/src/main/java/com/kipu/app/feature/statements/domain/ImportCardStatement.kt:45-58
- **Qué está mal:** `hasDuplicate` solo se evalúa al crear el import y solo si la tarjeta ya se resolvió sola (`card != null`). Si no hubo match (últimos 4 dígitos distintos o ausentes, varias tarjetas), todas las líneas nacen `selected=true, possibleDuplicate=false`. `setCard` para tarjetas de crédito solo cambia `card_id`/fingerprint; el bloque que recalcula duplicados y des-selecciona existe únicamente para `deposit` (débito).
- **Escenario de fallo:** el usuario ya registró (manual o por captura) la compra "TIENDA 50.00" del 30/12 en su Visa; importa el PDF, la tarjeta no se auto-asocia, la elige en el selector → la línea "TIENDA 50.00" sigue seleccionada y sin marca de duplicado; "Registrar" crea un segundo gasto con otra clave idempotente (la del estado) y duplica gasto y deuda de la tarjeta.
- **Fix mínimo:** en `setCard`, rama crédito, repetir el bucle de las líneas 96-100 usando `matching.hasDuplicate(owner, cardId, amount, currency, occurredDate)` + `dao.setDuplicate`. (Extraer el bucle a una función compartida por ambas ramas.)

#### [CAP1-03] La bandeja marca lista una captura sin cuenta
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/capture/presentation/CapturePresentation.kt:85-93; app/src/main/java/com/kipu/app/feature/capture/data/RoomCaptureInstrumentResolver.kt:18-26
- **Qué está mal:** La lista de datos faltantes solo revisa el tipo de instrumento y la ambigüedad, no que exista la cuenta o tarjeta identificada. El resolutor puede devolver el tipo sin identificador cuando no encuentra los últimos cuatro dígitos.
- **Escenario de fallo:** Un aviso de débito tiene monto y categoría conocidos, pero su tarjeta no coincide con ninguna cuenta activa: la bandeja lo presenta como listo aunque al abrirlo hay que elegir una cuenta.
- **Fix mínimo:** Marcar instrumento faltante cuando falte el identificador requerido por su tipo; agregar un test con tipo débito y `accountId = null`.

#### [CAP1-05] Un espacio de miles trunca el importe
- **Severidad:** media (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/capture/domain/NotificationCaptureParser.kt:68-75,174
- **Qué está mal:** La expresión termina el monto al encontrar un espacio y acepta el prefijo sin comprobar si continúa con más dígitos.
- **Escenario de fallo:** «Compra S/ 1 000 en TIENDA con tu tarjeta débito terminada en 1234 el 28/10/2026 10:35» produce S/ 1 en vez de S/ 1 000; con cuenta y categoría resueltas puede registrarse automáticamente.
- **Fix mínimo:** Reconocer la agrupación con espacios o rechazar el monto completo; probar ese aviso.
- **Verificación (Fable):** Confirmado: `MONEY` acepta un número sin espacios y corta en el primer espacio. Baja a media: los bancos peruanos suelen usar coma para los miles; falta una muestra real con espacio.

#### [CAP1-07] Una actualización del aviso crea otra captura
- **Severidad:** media · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/capture/domain/ExtractCaptureCandidate.kt:47-52,158-165; app/src/main/java/com/kipu/app/feature/capture/domain/CaptureCorrelation.kt:23-25
- **Qué está mal:** La huella incluye fecha y texto además de la clave. Una actualización sin referencia cambia la huella y la sugerencia excluye señales de la misma fuente.
- **Escenario de fallo:** El banco publica y actualiza el mismo aviso de S/ 25 con texto distinto y sin número de operación: se generan dos candidatos pendientes independientes, que el usuario puede registrar por separado.
- **Fix mínimo:** Presentar actualizaciones de una misma clave como posible duplicado si no puede probarse su identidad; probar dos entregas con la misma clave y texto distinto.

#### [CAP1-08] Se ignora el signo anterior al símbolo de moneda
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/capture/domain/NotificationCaptureParser.kt:68-75,138-140,174
- **Qué está mal:** Se rechaza el signo negativo después del símbolo, pero la búsqueda empieza en el símbolo y omite el signo situado antes.
- **Escenario de fallo:** «Compra -S/ 10 en TIENDA» se interpreta como gasto positivo de S/ 10 y no se marca como importe inválido.
- **Fix mínimo:** Rechazar también el signo negativo inmediatamente anterior al símbolo; probar `-S/ 10` y `S/ -10`.

#### [CAP2-02] Filas de resumen/cargo con importe negativo se importan como cargo positivo
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/statements/domain/BcpVisaStatementParser.kt:82-90 y 121-141
- **Qué está mal:** en las filas de resumen (intereses, seguros, comisiones) el total se suma con signo (`state.summary`, línea 86) pero la línea candidata se crea con `abs(amount)` y tipo INTEREST/INSURANCE/FEE seleccionada por defecto (línea 87). Lo mismo en `parseMovement`: operación `CARGO` con importe `5.00-` da `postedAmount = +amount`. El signo se pierde.
- **Escenario de fallo:** el PDF trae "EXTORNO INTERES MORATORIO 10.00-" (o una bonificación con "INTERES"/"SEGURO"/"COMISION" en el texto) → se propone un interés de +10.00 marcado para registrar; al confirmar sube la deuda de la tarjeta en vez de bajarla. La conciliación sí falla (banner), pero la línea sigue seleccionada.
- **Fix mínimo:** si `amount < 0` en esas dos ramas, emitir `StatementLineKind.REFUND` (o `UNKNOWN`, no seleccionada) con `abs`, y llevar el signo real a `posted`/`summary`. Alternativa más corta: `defaultSelected=false` para importes negativos.

#### [CAP2-03] El parser de ahorros descarta filas y páginas sin avisar; el banner de descuadre no señala nada
- **Severidad:** media · **Categoría:** datos · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/statements/domain/BcpSavingsStatementParser.kt:47-48 (página sin anclas se salta) y 70-72 (fila con `values.size != 1` se salta); app/src/main/java/com/kipu/app/feature/statements/presentation/StatementReviewScreen.kt:142-146
- **Qué está mal:** una fila con fecha pero con 0 o 2 importes legibles, o una página de continuación sin repetir "CARGOS / DEBE / ABONOS / HABER", se ignora en silencio (el parser Visa, en cambio, arrastra `columns` entre páginas). Lo único que lo delata es `reconciledPen=false`, que solo muestra un banner que dice "Revisa los movimientos marcados", pero ningún movimiento está marcado. La importación no se bloquea.
- **Escenario de fallo:** página 2 del PDF de ahorros sin cabecera repetida → se pierden todos sus movimientos; el usuario ve 12 de 30 líneas, un banner ambiguo, pulsa "Registrar" y su saldo queda sin esos movimientos.
- **Fix mínimo:** (a) reutilizar las anclas de la página anterior (variable fuera del bucle, como hace Visa) en vez de `continue`; (b) texto del banner sin "marcados" o marcar las líneas; opcional: pedir confirmación extra si `matches=false`.

#### [CAP2-04] Sin límite de tamaño ni de páginas al abrir el PDF: un OOM cierra la app
- **Severidad:** media · **Categoría:** escala · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/statements/data/PdfBoxStatementDocumentReader.kt:27-53
- **Qué está mal:** se carga todo con `MemoryUsageSetting.setupMainMemoryOnly()` (decisión de privacidad correcta) y se extrae texto de cada página a una lista de `StatementWord`, sin tope. Solo se capturan `IOException/SecurityException/IllegalArgumentException`; un `OutOfMemoryError` no se captura. El recognizer de recibos sí acota (12 MB, 40 MP).
- **Escenario de fallo:** el usuario elige por error un PDF de 150 páginas / 80 MB desde el selector → PDFBox agota el heap y Android mata la app, sin mensaje.
- **Fix mínimo:** leer con tope (como `readBounded` de recibos): rechazar > ~15 MB o > ~30 páginas devolviendo `Unreadable`.

#### [CAP2-05] La tarjeta "Registrados desde Yape" recarga todos los movimientos en cada cambio
- **Severidad:** media · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/receipts/data/local/RoomReceiptCandidateStore.kt:94-111
- **Qué está mal:** `observeRegistered` combina `observeConfirmed` con `movements.observeTransactions(owner)` (toda la tabla del usuario), construye un mapa de todos los activos y hace un `getReceipt` por cada candidato confirmado hasta juntar 3. Solo se muestran 3 filas.
- **Escenario de fallo:** usuario con 5.000 movimientos y 300 recibos confirmados abre la bandeja de recibos: cada escritura en `transactions` (también de sync) vuelve a emitir y recorre las 5.000 filas + hasta 300 consultas.
- **Fix mínimo:** una consulta DAO con JOIN entre los recibos confirmados y `transactions` activas, `ORDER BY updated_at DESC LIMIT 3`.

#### [CAP2-12] Tests que no prueban nada o que nunca se ejecutan en CI
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/feature/receipts/domain/YapeReceiptParserTest.kt:365-390; app/src/test/java/com/kipu/app/feature/statements/BcpSavingsLocalStatementTest.kt:23-47 y 49-77; app/src/test/java/com/kipu/app/feature/statements/PdfBoxStatementDocumentReaderTest.kt:80-102
- **Qué está mal:** (1) `same amount but different reference are not equal` compara dos `data class` con `assertNotEquals`: prueba el compilador, no el parser. (2) `real local PDFs report merchant suggestion coverage` termina en `assertTrue(suggested in 0..total)`, que no puede fallar. (3) Los tres tests con PDFs reales dependen de `docs/EECC/*.pdf`, que no está versionado (`git ls-files docs/EECC` vacío) y usan `assumeTrue`: en CI se saltan siempre, así que la conciliación sobre PDFs reales no está protegida.
- **Escenario de fallo:** un cambio en `BcpVisaStatementParser.findColumns` rompe el parseo de PDFs reales y la suite sigue en verde.
- **Fix mínimo:** borrar (1) y (2) (~45 líneas); para (3), mantener un PDF sintético anonimizado como fixture o documentar que es solo manual.
- **Ahorro estimado:** 45 líneas

#### [CAP2-15] La prueba de avisos no comprueba las acciones del comprobante duplicado
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/receipts/presentation/ReceiptExperienceTest.kt:95-106; app/src/main/java/com/kipu/app/feature/receipts/presentation/ReceiptImportNoticeSheet.kt:90-102
- **Qué está mal:** la prueba `allImportNoticesExposeTheirMatchingActionAndAnotherImage` solo comprueba textos; todos los callbacks son vacíos y nunca pulsa el botón principal. No detecta que un aviso invoque la acción de otro estado.
- **Escenario de fallo:** si `ALREADY_DISCARDED` se conecta por error a `onMovement` en vez de `onRestore`, la prueba sigue en verde aunque «Agregar de nuevo» no restaure el comprobante.
- **Fix mínimo:** pulsar `receipt_notice_primary` para `ALREADY_DISCARDED` y comprobar que se invoca `onRestore` y no `onMovement`.

#### [CAP2-16] Un error inesperado de lectura desaparece sin aviso
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/statements/presentation/StatementImportViewModel.kt:219-226; app/src/main/java/com/kipu/app/feature/statements/presentation/ImportMovementsScreen.kt:147-207
- **Qué está mal:** el ViewModel guarda el mensaje de error en `state.message`, pero la pantalla de importación no lo muestra. El mismo problema afecta los errores al cargar borradores o limpiar vencidos.
- **Escenario de fallo:** el lector o el almacenamiento lanza una excepción inesperada → termina la animación de lectura y vuelve la pantalla inicial sin explicar que falló ni cómo reintentar.
- **Fix mínimo:** mostrar `state.message` en la pantalla y conectar una acción para descartarlo; añadir un test que fuerce una excepción y compruebe que se ve el mensaje.

#### [CAP2-17] Cancelar la edición conserva el comercio y puede cambiar la categoría
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/statements/presentation/StatementLineEditSheet.kt:117-118,131-137,172-173; app/src/main/java/com/kipu/app/feature/statements/presentation/StatementReviewViewModel.kt:229-235; app/src/main/java/com/kipu/app/feature/statements/domain/PrepareStatementImportReview.kt:89-94
- **Qué está mal:** elegir, aceptar o quitar un comercio dentro de «Editar movimiento» lo guarda inmediatamente en el borrador; al elegir uno también puede reemplazar la categoría. El botón «Cancelar» solo cierra la hoja y no revierte esos cambios, a diferencia del importe, fecha y descripción que sí esperan a «Guardar cambios».
- **Escenario de fallo:** una compra tiene categoría Transporte; el usuario abre la edición, elige por error un comercio con categoría Comida y pulsa «Cancelar» → el borrador queda con ese comercio y categoría Comida, que después puede registrar sin advertencia.
- **Fix mínimo:** mantener comercio y categoría propuestos en el estado temporal de la hoja y persistirlos juntos al pulsar «Guardar cambios»; cancelar debe descartar ambos. Añadir un test que seleccione el comercio, cancele y compruebe que no cambia el borrador.

#### [CAP2-06] Conciliar una devolución carga todos los movimientos dentro de una transacción de escritura
- **Severidad:** baja · **Categoría:** escala · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/statements/domain/ConfirmStatementImport.kt:239-256 (llamado desde la 218, dentro de `transactions { }` en la 102)
- **Qué está mal:** `refundPurchase` hace `movements.observeTransactions(ownerId).first()` (todo el historial), filtra en memoria y llama `refunds.getRefundedAmount` por candidato; se ejecuta por cada línea REFUND con la transacción de Room abierta.
- **Escenario de fallo:** estado con 8 devoluciones y 6.000 movimientos → 8 cargas completas del historial con el lock de escritura tomado; otras pantallas que escriben se bloquean unos segundos.
- **Fix mínimo:** consulta DAO acotada por `card_id`, moneda y ventana de fechas (ya se filtra por 120 días) en lugar de cargar todo.

#### [CAP2-07] Confirmar un recibo no es atómico y tiene un reintento sin sentido
- **Severidad:** baja · **Categoría:** datos · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/receipts/domain/ConfirmReceiptCandidate.kt:23-37
- **Qué está mal:** `store.edit`, `registerTransaction` y `markConfirmed` son tres operaciones separadas, sin `DatabaseTransactionRunner` (statements sí lo usa). Además `if (Success && !markConfirmed) markConfirmed(...)` reintenta inmediatamente la misma llamada e ignora el resultado.
- **Escenario de fallo:** el proceso muere entre `registerTransaction` y `markConfirmed` → el movimiento existe pero el candidato sigue PENDING en la bandeja; el usuario lo ve "listo" y lo confirma otra vez (la clave idempotente evita duplicar, pero la UX es confusa).
- **Fix mínimo:** envolver las tres llamadas en `transactions { }` y borrar el segundo `markConfirmed`.

#### [CAP2-08] Los motivos de fallo de pago/registro se deducen del texto del mensaje de error
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/statements/domain/ConfirmStatementImport.kt:177-180 y 258-264
- **Qué está mal:** `contains("Insufficient funds")`, `"exceeds current card debt"`, `"currency mismatch"` y, como último caso, `contains("account")` → `SourceAccountInvalid`. Si alguien cambia o traduce el mensaje en `PayCreditCard`/`RegisterTransaction`, el motivo cae a `RegistrationFailed`; y cualquier mensaje que contenga "account" se muestra como cuenta de origen inválida.
- **Escenario de fallo:** PayCreditCard falla con "Credit card account archived" → el usuario lee "La cuenta de origen no está disponible o tiene otra moneda" y busca el problema en la cuenta equivocada.
- **Fix mínimo:** que los casos de uso devuelvan un error tipado y mapear por tipo; mientras, quitar el `contains("account")`.
- **Cross-módulo:** sí → ACC1 (mensajes de `PayCreditCard`) / MOV1 (`RegisterTransactionResult.Failure`)

#### [CAP2-09] Moneda de la cuenta de ahorros: se busca "SOLES" antes que "DOLARES" en toda la primera página
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** baja · **Estado:** probable (confianza baja del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/statements/domain/BcpSavingsStatementParser.kt:27,33-37
- **Qué está mal:** `header` es el texto completo de la página 1 y se decide PEN si aparece la palabra suelta SOLES en cualquier lugar.
- **Escenario de fallo:** estado de una cuenta en dólares cuya primera página menciona "soles" (tipo de cambio, nota al pie) → se lee como PEN. No se registra mal por la comprobación `CurrencyMismatch` posterior, pero la cuenta débito USD no se puede elegir y las cifras salen con "S/".
- **Fix mínimo:** tomar la moneda de la celda bajo la cabecera "MONEDA" (el test ya la posiciona, `500f to "SOLES"`) en vez de buscar en todo el texto.

#### [CAP2-10] Yape: "S/ 1,500" o "1.500" (miles sin decimales) se descarta como inválido
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/receipts/domain/YapeReceiptParser.kt:197-215
- **Qué está mal:** con un solo separador y 3 dígitos tras él se devuelve `Invalid`, aunque en Perú la coma es separador de miles y ningún importe en soles tiene 3 decimales. No inventa un valor (es seguro), pero el candidato nace sin monto.
- **Escenario de fallo:** comprobante "¡Yapeaste! S/ 1,500" → `amountMinor=null`, `MISSING_AMOUNT`; el usuario debe teclear el monto aunque el OCR lo leyó bien. No hay test (los de miles solo usan `1,200.50` y `1.200,50`).
- **Fix mínimo:** en la rama `count == 1` con `fracStr.length == 3`, tratar la coma como miles (`1,500` → 150000) y dejar `.` con 3 dígitos como inválido; añadir un test.

#### [CAP2-11] Los borradores vencidos de estados de cuenta solo se borran cuando el usuario vuelve a importar
- **Severidad:** baja · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/statements/domain/ImportCardStatement.kt:32; app/src/main/java/com/kipu/app/feature/statements/presentation/StatementImportViewModel.kt:83; app/src/main/java/com/kipu/app/feature/statements/data/local/RoomStatementImportStore.kt:61,141
- **Qué está mal:** `observe` oculta los borradores vencidos (7 días) pero el `DELETE` solo corre al abrir la pantalla de importar o al importar (grep: no hay worker; el de capture no toca esta tabla). Las líneas guardan descripciones de comercio e importes del banco.
- **Escenario de fallo:** el usuario importa un PDF, no confirma y no vuelve a la función → descripciones y montos de su estado de cuenta quedan en la base local indefinidamente aunque la UI dice que vencen.
- **Fix mínimo:** llamar `purgeExpired` desde el worker de purga existente (`EvidencePurgeWorker`) o al iniciar sesión.
- **Cross-módulo:** sí → CAP1 (el worker vive allí; la llamada se añadiría ahí)

#### [CAP2-13] Los dos parsers BCP duplican utilidades y ya divergen en la validación de fechas
- **Severidad:** baja · **Categoría:** fusionar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/statements/domain/BcpSavingsStatementParser.kt:29,52-56,98-100 vs BcpVisaStatementParser.kt:185-193,234-241
- **Qué está mal:** `inferDate`, el mapa de meses, la regex `shortDate` y la lectura de fechas/importes están copiados. Visa usa `ResolverStyle.STRICT` (31/02/26 es inválido); ahorros usa `DateTimeFormatter.ofPattern(...)` con el resolver por defecto (SMART), que ajusta 31/02/26 a 28/02/26 sin error. Además el `money` de ahorros no admite signo y el de Visa sí.
- **Escenario de fallo:** un periodo "DEL 01/02/26 AL 31/02/26" mal extraído se acepta como válido en ahorros y se rechaza en Visa.
- **Fix mínimo:** mover `inferDate`, `months`, `shortDate`, `parseMinor` y el formateador estricto a `StatementLayout.kt` (internal) y usarlos en ambos.
- **Ahorro estimado:** 25 líneas

#### [CAP2-14] Código muerto en recibos y statements
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/receipts/presentation/ReceiptReviewScreen.kt:422-437 (`ReceiptShareConsentDialog`, sin usos) y 93,98 (`onEdit` con `@Suppress("UNUSED_PARAMETER")`); app/src/main/java/com/kipu/app/feature/receipts/data/local/ReceiptCandidateDao.kt:14-15 (`all`); app/src/main/java/com/kipu/app/feature/statements/data/local/StatementImportDao.kt:37-38 (`observe`); app/src/main/java/com/kipu/app/feature/statements/presentation/StatementDesign.kt:90-91 (`maybeOwnTransfer()`, envoltorio de `possibleOwnTransfer`)
- **Qué está mal:** grep en `app/src` (main, test, androidTest) no encuentra otros usos de `ReceiptShareConsentDialog`, `ReceiptCandidateDao.all` ni `StatementImportDao.observe`; `onEdit` se pasa en las llamadas pero nunca se lee.
- **Escenario de fallo:** no hay fallo; son líneas que mantener, y un diálogo de consentimiento sin uso puede desincronizarse del texto real de consentimiento.
- **Fix mínimo:** borrar los símbolos, quitar `onEdit` y sus argumentos (tests de UI en androidTest, incluido `MovementSwipeReceiptsTest` de movements), sustituir `maybeOwnTransfer()` por `possibleOwnTransfer`.
- **Ahorro estimado:** 30 líneas

#### [CAP2-18] El mensaje de éxito afirma que la deuda refleja todo el estado tras importar solo una parte
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/statements/presentation/StatementReviewScreen.kt:567-586; app/src/test/java/com/kipu/app/feature/statements/ConfirmStatementImportTest.kt:403-414
- **Qué está mal:** el mensaje de éxito afirma que la deuda refleja el estado de cuenta aunque la confirmación admite excluir líneas y completar la importación con solo las seleccionadas.
- **Escenario de fallo:** el usuario desmarca una compra de S/ 100 y registra las demás → la app confirma que la deuda refleja el estado, aunque esa compra no se agregó.
- **Fix mínimo:** decir que se registraron los movimientos seleccionados y que actualizaron la deuda, sin afirmar que refleja todo el estado.

### Base de datos local

Porciones: DB1 (Claude sonnet medium (relevo)). Hallazgos: 13 (6 media, 7 baja).

#### [DB1-01] Los triggers de estado de cuotas congelan el SQL del momento de instalación
- **Severidad:** media · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt:1457-1473, 1493-1498
- **Qué está mal:** `ensureCreditInstallmentStatusGuards` crea los triggers con el texto de `REFRESH_CREDIT_INSTALLMENT_STATUSES_SQL` y `CREATE TRIGGER IF NOT EXISTS`. Los triggers viven en SQLite, no en el schema de Room, así que si ese SQL cambia en el futuro, los dispositivos ya instalados conservan el trigger viejo y las instalaciones nuevas (vía `MovementSchemaCallback.onCreate`) reciben el nuevo.
- **Escenario de fallo:** se corrige `CREDIT_INSTALLMENT_BALANCES_SQL` (por ejemplo un estado más que cuenta como pago) y se sube la versión sin recrear triggers. El usuario antiguo ve la cuota PENDING tras un pago; el usuario nuevo la ve PAID, con los mismos datos. Room no valida triggers, así que ningún test de schema lo detecta.
- **Fix mínimo:** hacer `DROP TRIGGER IF EXISTS` antes de cada `CREATE TRIGGER` y llamar a `ensureCreditInstallmentStatusGuards` también desde `Callback.onOpen` (o desde cada migración que toque ese SQL).

#### [DB1-02] Cada cambio de status/sync_status de cualquier movimiento recalcula todas las cuotas del usuario
- **Severidad:** media · **Categoría:** rendimiento · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt:1461, 1465-1471 (SQL en feature/accounts/data/local/CreditEntities.kt:280-289)
- **Qué está mal:** el trigger `AFTER UPDATE OF status, sync_status ON transactions` no tiene cláusula `WHEN`, y ejecuta un `UPDATE credit_installments ... WHERE user_id = NEW.user_id` que reevalúa un CTE con joins (allocations, refunds, cards, catálogo de comercios) por cada cuota del usuario.
- **Escenario de fallo:** un usuario con 3.000 movimientos pendientes (uso offline) sincroniza; cada ack cambia `sync_status` y dispara el recálculo completo de todas sus cuotas, aunque el movimiento sea un gasto en efectivo sin relación con tarjetas. Son miles de recálculos dentro de las transacciones de sync: lentitud notable y bloqueo de escritura de la base.
- **Fix mínimo:** añadir `WHEN NEW.card_id IS NOT NULL OR NEW.operation_kind IN ('CARD_PURCHASE','CARD_PAYMENT','REFUND')` a los triggers de `transactions` (y acotar el UPDATE a las cuotas de esa transacción).

#### [DB1-03] `SecureLog` reemplaza la excepción por una nueva y pierde stack trace y causa
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/core/logging/LogRedactor.kt:49-68
- **Qué está mal:** `sanitizeThrowable` devuelve `RuntimeException(message)` creada dentro de `SecureLog`; su stack trace apunta a `SecureLog.sanitizeThrowable`, y el tipo y la causa originales se pierden. Hay ~11 llamadas con throwable (p. ej. KeystoreEncryptedSessionStorage.kt:97/122/136, AuthApi.kt:78/103, SupabaseAuthRepository.kt:188).
- **Escenario de fallo:** falla el descifrado de la sesión (`SecureLog.w(TAG, "Failed to decrypt session...", e)`); en logcat solo se ve un `RuntimeException` con frames de logging, sin saber si fue `AEADBadTagException`, `KeyStoreException`, etc. Diagnosticar un cierre de sesión inesperado en campo es casi imposible.
- **Fix mínimo:** `RuntimeException(redactedMessage, throwable.cause).apply { stackTrace = throwable.stackTrace }`, o pasar el throwable original a `Log.w/Log.e` (el mensaje ya se redacta aparte).

#### [DB1-04] Migraciones que ejecutan código de dominio vivo y un `replace` frágil sobre SQL
- **Severidad:** media · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt:723, 1424-1430, 1452, 1563, 1605
- **Qué está mal:** 31→32 usa `BillingCycleCalculator`/`CycleDateBasis`, 14→15 usa `CreditLiabilityAccountIds`, 36→37 usa `CaptureCorrelation.reference`, 39→40 usa `receiptRegistrationKey`, y 31→32 usa `REFRESH_CREDIT_INSTALLMENT_STATUSES_SQL.replace("WHERE user_id = :userId AND", "WHERE 1 = 1 AND")`. Una migración debe ser una foto inmutable; aquí su resultado cambia cuando cambia el código de la app.
- **Escenario de fallo:** (a) se cambia el esquema del UUID de `receiptRegistrationKey`; un usuario que salta de v39 a la nueva versión no obtiene la coincidencia y sus movimientos OCR quedan como `MANUAL`. (b) se reformatea el SQL de `CreditEntities.kt` (espacios o salto de línea en `WHERE user_id = :userId AND`): el `replace` no encuentra el texto, queda `:userId` sin enlazar, `execSQL` lanza y la migración 31→32 falla; Room la reintenta en cada arranque y la app queda en bucle de cierre (no hay `fallbackToDestructiveMigration`).
- **Fix mínimo:** copiar a la migración la lógica mínima (UUID v3 de una línea, normalización de referencia, SQL de refresco literal con `WHERE 1 = 1`) en lugar de importar dominio; como mínimo, un `check(sql != replaced)` tras el `replace`.

#### [DB1-05] Cobertura mínima del redactor de logs (2 casos para 6 regex)
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/core/logging/MovementLogRedactionTest.kt:10-25
- **Qué está mal:** solo se prueban JWT/Bearer y email. `PASSWORD_JSON_REGEX`, `PASSWORD_PARAM_REGEX`, `RECOVERY_TOKEN_REGEX` y `SecureLog` no tienen ningún test; es código de seguridad (FR-006/FR-043).
- **Escenario de fallo:** alguien "simplifica" `RECOVERY_TOKEN_REGEX` y los `recovery_token=...` pasan a logcat en claro; ningún test falla. - Fix mínimo (test): un test con 4 entradas (`"password":"x"`, `password=x`, `recovery_token=abc`, `refresh_token=abc`) que compruebe que la salida no contiene el secreto.
- **Fix mínimo:** —

#### [DB1-06] Los triggers de estado de cuotas y su paridad instalación nueva/migrada no tienen test
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/core/database/MovementRoomMigrationTest.kt:100-118 (solo cuenta triggers `immutable_%` y `movement_head_owner_%`)
- **Qué está mal:** no encontré en `androidTest` ni `test` ninguna referencia a `credit_installment_status_*` ni a `ensureCreditInstallmentStatusGuards`; el estado PAID/PARTIAL de dinero depende de esos triggers.
- **Escenario de fallo:** se renombra una tabla/columna y `onCreate` deja de crear algún trigger sin que nada falle; en instalaciones nuevas la cuota sigue PENDING tras pagar. - Fix mínimo (test): un test de instrumentación que cree la base con `MovementSchemaCallback`, inserte compra + asignación de pago y compruebe que la cuota pasa a PAID; y que el conjunto de nombres `credit_installment_status_%` es el mismo en base nueva y tras migrar 31→32.
- **Fix mínimo:** —

#### [DB1-07] Fecha de vencimiento del saldo de crédito heredado calculada en UTC
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt:629
- **Qué está mal:** `date(t.occurred_at / 1000, 'unixepoch')` usa fecha UTC; el resto del sistema usa America/Lima (la propia migración 31→32 en la línea 1428).
- **Escenario de fallo:** el último movimiento de una tarjeta es a las 20:00 hora de Lima (01:00 UTC del día siguiente); la cuota del saldo heredado queda con `due_date` un día después del real. Solo afecta a usuarios que migraron desde la versión 10.
- **Fix mínimo:** `date(t.occurred_at / 1000 - 18000, 'unixepoch')` (Lima es UTC-5 sin horario de verano).

#### [DB1-08] Migraciones con `check()` estricto que impiden abrir la base sin camino de recuperación
- **Severidad:** baja · **Categoría:** datos · **Confianza del worker:** baja · **Estado:** probable (confianza baja del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt:416-418, 768-806 (y CoreModule.kt:27-71, sin fallback)
- **Qué está mal:** 5→6 y 14→15 abortan con `IllegalStateException` si encuentran importes fuera de rango, pagos con asignaciones que no suman o ledger duplicado. Es una decisión consciente (no corromper dinero), pero el resultado es un cierre en cada arranque.
- **Escenario de fallo:** un usuario que sigue en v14 con una asignación de pago incompleta actualiza: la app no abre nunca más, sin mensaje ni forma de exportar sus datos.
- **Fix mínimo:** si ya no quedan usuarios en versiones menores a 15, borrar esas migraciones y subir la versión mínima soportada; si quedan, mostrar una pantalla de recuperación al fallar el arranque.

#### [DB1-09] `MerchantLogoUrlResolver` deja pasar `..` en la clave del logo
- **Severidad:** baja · **Categoría:** seguridad · **Confianza del worker:** baja · **Estado:** probable (confianza baja del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/core/storage/data/MerchantLogoUrlResolver.kt:13-15 (test en MerchantLogoUrlResolverTest.kt:28-42)
- **Qué está mal:** `Uri.encode(key, "/")` no codifica `.`, así que `logo-dev/../../otro-bucket/x.png` se normaliza por el cliente HTTP a otro objeto público del mismo proyecto.
- **Escenario de fallo:** una fila del catálogo con `logo_path` malicioso o erróneo hace que la app pida un archivo de otro bucket público; el impacto es bajo (solo lectura de objetos ya públicos) y requiere escribir en el catálogo global.
- **Fix mínimo:** `if (key.split('/').any { it == ".." || it == "." }) return null` más un caso en el test.
- **Cross-módulo:** sí → CAT1 (quién escribe `logo_path` en el catálogo)

#### [DB1-10] Converters de enum lanzan con valores desconocidos
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/core/database/DatabaseConverters.kt:32, 34
- **Qué está mal:** `PlanSelection.valueOf` y `OutboxStatus.valueOf` lanzan `IllegalArgumentException` ante un nombre desconocido, lo que rompe cualquier lectura de la tabla. Room tampoco tiene política de downgrade (instalar un APK más antiguo con base v40 lanza `IllegalStateException` al abrir).
- **Escenario de fallo:** se renombra `OutboxStatus.TERMINAL_ERROR` sin migración de datos, o se hace rollback a una compilación anterior: la app se cae al leer `sync_outbox`/`plan_preferences`.
- **Fix mínimo:** `runCatching { valueOf(it) }.getOrNull()` con valor por defecto en las dos conversiones de planes.

#### [DB1-11] Tests duplicados y mal ubicados
- **Severidad:** baja · **Categoría:** fusionar · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/core/database/MovementRoomMigrationTest.kt:57-98 vs DebtRoomMigrationTest.kt:19-46; app/src/test/java/com/kipu/app/core/storage/data/MerchantLogoUrlResolverTest.kt:44-68
- **Qué está mal:** `MovementRoomMigrationTest` prueba la migración 19→20 (deudas), que ya cubre `DebtRoomMigrationTest` (18→20); `MerchantLogoUrlResolverTest` contiene dos tests de `MerchantLogoPrefetchPlan`, que no es de esta clase ni de este paquete.
- **Escenario de fallo:** al cambiar la migración 19→20 hay que actualizar dos tests; los tests de `MerchantLogoPrefetchPlan` no aparecen donde se buscan.
- **Fix mínimo:** borrar el caso de deudas de `MovementRoomMigrationTest` y mover los dos tests de prefetch a un `MerchantLogoPrefetchPlanTest` en categories.
- **Ahorro estimado:** 40 líneas

#### [DB1-12] Redactor: falsos positivos y huecos (refresh_token JSON, tarjetas)
- **Severidad:** baja · **Categoría:** seguridad · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/core/logging/LogRedactor.kt:15-16
- **Qué está mal:** `PASSWORD_PARAM_REGEX` incluye espacio como separador, así que "Sync pass completed" se convierte en "Sync pass [REDACTED_PASSWORD]" (se pierde información de diagnóstico), mientras `RECOVERY_TOKEN_REGEX` exige `=`: `"refresh_token":"abcdefghijkl"` (token opaco, no JWT) pasa sin redactar. Tampoco hay patrón para números de tarjeta de 13-19 dígitos pese a que el KDoc promete "datos financieros sensibles".
- **Escenario de fallo:** un cuerpo de error de auth con `"refresh_token":"..."` se registra por un `SecureLog.e` futuro y el token queda en logcat.
- **Fix mínimo:** cambiar `[:= ]` por `[:=]` en la línea 15 y permitir `["']?\s*[:=]\s*["']?` en la 16.

#### [DB1-13] `SecureLog.d` sin llamadas
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/core/logging/LogRedactor.kt:41-43
- **Qué está mal:** grep en `app/src/main` solo encuentra usos de `SecureLog.e` (17), `.w` (35) y `.i` (5); `.d` no se llama.
- **Escenario de fallo:** ninguno funcional; código muerto que mantener.
- **Fix mínimo:** borrar la función.
- **Ahorro estimado:** 4 líneas

### UI ligera (home, analytics, settings, notificaciones, widgets, portabilidad, navegación)

Porciones: UI1 (Gemini 3.8 flash-high); UI2 (Gemini 3.8 flash-high); UI3 (Codex gpt-6.1-terra medium); NAV1 (Gemini 3.8 flash-high). Hallazgos: 62 (8 alta, 34 media, 20 baja).

#### [UI1-02] La vista de comparación (`AnalyticsComparison`) oculta y falla ante periodos que solo tienen ingresos
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsScreen.kt:824-829 y app/src/main/java/com/kipu/app/feature/analytics/domain/Analytics.kt:170-179
- **Qué está mal:** En `AnalyticsScreen.kt`, `comparisonItems` verifica: `if (!result.previous.hasRecords && !result.current.hasRecords)` para mostrar el estado vacío `comparison_empty`. Pero `hasRecords` solo verifica si hay registros de gastos (`matched.isNotEmpty()`). Si un usuario aplica el filtro de tipo "Ingresos" (`MovementType.INCOME`), o si en los periodos solo hay ingresos y no gastos, `hasRecords` es falso en ambos, y la UI muestra erróneamente "Sin movimientos en los periodos seleccionados" (`analytics_comparison_empty`). Además, `AnalyticsCalculator.compare` solo itera sobre `previous.totals.keys + current.totals.keys` (que son exclusivamente totales de gastos) y omite por completo `incomeTotals`.
- **Escenario de fallo:** Un usuario con ingresos en octubre y noviembre entra a "Comparar" y en el filtro selecciona Tipo: "Ingresos". La pantalla muestra el estado vacío "Sin movimientos en los periodos seleccionados" a pesar de tener ingresos en ambos meses.
- **Fix mínimo:** En `comparisonItems` verificar `!result.previous.hasMovements && !result.current.hasMovements`. En `AnalyticsCalculator.compare`, si se filtran ingresos, comparar `incomeTotals` o deshabilitar/ocultar el filtro de ingresos en modo comparación.

#### [UI2-01] Bloqueo irreversible en estado CONFLICT de sincronización de perfil por persistencia de revisión obsoleta
- **Severidad:** alta (el worker dijo crítica) · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/settings/data/OfflineFirstProfilePreferencesRepository.kt:181-188 y app/src/main/java/com/kipu/app/feature/settings/data/local/ProfilePreferencesDao.kt:54-78, 87-98
- **Qué está mal:** Cuando `update_profile_preferences` en Supabase detecta una divergencia de versiones (`p_expected_revision != revision`), devuelve `"CONFLICT"`. El repositorio marca la fila de outbox como `"CONFLICT"`, el perfil local como `SyncState.CONFLICT` y falla. En Room, `savePreferencesWithOutbox` solo busca filas con `status = 'PENDING'` (`findOldestPendingOutbox`); al haber una fila previa en conflicto, no la fusiona y crea una nueva fila con la revisión local antigua (`expectedRevision = current.remoteRevision`). Peor aún, al invocar `refreshProfile(userId)` para intentar recuperarse contra el servidor, `ProfilePreferencesDao.hydrateProfile` busca filas no resueltas mediante `findUnresolvedOutbox`. Como la fila con `status = 'CONFLICT'` sigue existiendo en el outbox, `unresolved.isNotEmpty()` es verdadero y `hydrateProfile` sobreescribe la revisión remota entrante con la revisión local obsoleta (`remoteRevision = local.remoteRevision, syncState = local.syncState`). Como resultado, el perfil queda atrapado para siempre en conflicto: ninguna sincronización futura tendrá éxito y ningún refresco remoto podrá elevar la revisión local.
- **Escenario de fallo:** Un usuario modifica una preferencia (ej. cambia de tema o divisa) en otro dispositivo o en la web, elevando la revisión en Supabase a 2. En el dispositivo local (en revisión 1), cambia el día de inicio contable a 5. El worker de sincronización intenta aplicar el cambio con `p_expected_revision = 1`; el servidor responde `CONFLICT`. El outbox queda en estado `CONFLICT`. Cuando el usuario abre Ajustes, la app invoca `refreshProfile`, el cual obtiene del servidor la revisión 2; sin embargo, `hydrateProfile` detecta el outbox en `CONFLICT`, descarta la revisión 2 y reescribe `remoteRevision = 1`. A partir de ese momento, cualquier ajuste futuro del usuario se emitirá con revisión 1 y chocará indefinidamente contra el servidor sin posibilidad de recuperación.
- **Fix mínimo:** En `ProfilePreferencesDao.hydrateProfile`, si las outboxes no resueltas están en estado `CONFLICT`, no forzar `remoteRevision = local.remoteRevision`; adoptar `remote.remoteRevision` del servidor y marcar o purgar las outboxes conflictivas para permitir que las nuevas ediciones locales partan de la revisión fresca del servidor.
- **Verificación (Fable):** Confirmado: una outbox en CONFLICT cuenta como no resuelta, `hydrateProfile` conserva la `remoteRevision` local y las ediciones nuevas no se fusionan con ella (solo busca PENDING), así que vuelven a chocar. Baja a alta: afecta a preferencias, no a dinero.

#### [UI2-02] Corrupción financiera de saldo inicial al ingresar decimales con coma en `SetupAccountDraft` (error de 100x)
- **Severidad:** alta (el worker dijo crítica) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/setup/SetupDraft.kt:43-50 y app/src/main/java/com/kipu/app/feature/setup/SetupScreens.kt:173
- **Qué está mal:** En `SetupDraft.balanceMinorUnits`, el cálculo del importe en céntimos realiza: `val text = balanceText.trim().replace(",", "")` `val value = text.toBigDecimalOrNull() ?: return null` `return value.setScale(2, RoundingMode.HALF_UP).multiply(BigDecimal(100)).toLong()` En Perú y en dispositivos Android configurados en español, el teclado numérico de tipo `KeyboardType.Decimal` frecuentemente suministra o permite ingresar la coma `,` como separador decimal. Al ejecutar `replace(",", "")` incondicionalmente, un saldo de `150,50` o `12,50` se transforma en `"15050"` o `"1250"`, multiplicando el valor por 100x. El saldo resultante en `balanceMinorUnits` es `1505000L` (S/ 15,050.00 en vez de S/ 150.50).
- **Escenario de fallo:** Un usuario nuevo en el paso 1 del setup elige cuenta bancaria BCP e ingresa `150,50` soles en el campo "Saldo inicial". `balanceMinorUnits` calcula `1505000L`. `SetupFinalizer` crea la cuenta y su balance de apertura con S/ 15,050.00 en el libro contable de Room en vez de S/ 150.50, corrompiendo el patrimonio inicial del usuario en cien veces su valor real.
- **Fix mínimo:** Normalizar la coma decimal antes de purgar separadores de miles: si el texto contiene coma y no punto, reemplazar `,` por `.`: `val text = if ("," in balanceText && "." !in balanceText) balanceText.trim().replace(',', '.') else balanceText.trim().replace(",", "")`.
- **Verificación (Fable):** Confirmado: `replace(",", "")` convierte «150,50» en 15050.00. `KeyboardType.Decimal` ofrece coma con teclados en español. Baja a alta: el saldo inicial se corrige después y el texto de ayuda dice «150.00».

#### [UI2-04] Bloqueo permanente de desactivación biométrica si el dispositivo pierde la capacidad de autenticar
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/settings/presentation/BiometricSettingsScreen.kt:211-215 y app/src/main/java/com/kipu/app/feature/settings/presentation/BiometricSettingsViewModel.kt:83-89
- **Qué está mal:** En `BiometricSettingsScreen.kt`, el `Switch` de activación/desactivación del bloqueo biométrico define: `Switch(checked = state.isLocalUnlockEnabled, onCheckedChange = { onToggleLocalUnlock() }, enabled = state.canAuthenticate)` Si un usuario tenía el bloqueo local activado y luego desconfigura su huella digital, borra su PIN de pantalla en Android o sufre una falla de hardware en el sensor biométrico, `canAuthenticate` pasa a ser `false`. Como el `Switch` queda inhabilitado con `enabled = false`, el usuario NO PUEDE apagar el bloqueo local desde la interfaz. Cada vez que la app entra en segundo plano, `LocalLockCoordinator` activará la pantalla de bloqueo pidiendo una autenticación imposible de cumplir, bloqueando al usuario fuera de su cuenta sin opción de desactivación.
- **Escenario de fallo:** Usuario con "Bloqueo Local" activado decide borrar sus huellas dactilares registradas en los ajustes de Android. Abre Kipu para desactivar la protección biométrica en la app. Al entrar a Ajustes > Bloqueo local, encuentra el switch apagado/gris sin posibilidad de tocarlo. Al salir de la pantalla, la app se bloquea y el usuario no puede volver a ingresar.
- **Fix mínimo:** Permitir la interacción del switch siempre que ya esté activo: `enabled = state.canAuthenticate || state.isLocalUnlockEnabled`.

#### [UI3-02] Los widgets muestran datos con la aplicación bloqueada
- **Severidad:** alta · **Categoría:** seguridad · **Confianza del worker:** alta · **Estado:** probable
- **Ubicación:** app/src/main/java/com/kipu/app/feature/widgets/WidgetRefresher.kt:89
- **Qué está mal:** el estado `Protected`, que incluye `APP_LOCKED`, conserva el propietario para los widgets; `WidgetDataProvider.load()` lee ese identificador sin comprobar el bloqueo y presenta datos financieros.
- **Escenario de fallo:** con bloqueo biométrico activo y saldos visibles, otra persona mira la pantalla de inicio y ve importes, nombres de acreedores o capturas sin desbloquear Kipu.
- **Fix mínimo:** al bloquear, redibujar inmediatamente un estado protegido sin datos y mantenerlo también cuando un proceso nuevo atienda una actualización del widget; restaurar la información solo tras desbloquear.
- **Verificación (Fable):** Los widgets recuerdan al dueño también en `LocalAccess.Protected`. Decisión de producto.

#### [UI3-03] Una actualización simultánea pierde el descarte de un aviso
- **Severidad:** alta · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/notifications/data/OfflineFirstNotificationsRepository.kt:160
- **Qué está mal:** el envío lee una copia de la cola, mientras un descarte posterior reutiliza el mismo `operationId`; al terminar el primer envío se elimina esa fila por identificador, aunque su contenido haya cambiado.
- **Escenario de fallo:** se envía «leído» y, antes de la respuesta, el usuario descarta el mismo aviso; la respuesta del primer envío borra también el descarte pendiente y otro dispositivo mantiene el aviso.
- **Fix mínimo:** borrar únicamente si la fila todavía coincide con el estado enviado, o asignar una nueva versión de operación al cambiarla; probar la intercalación entre envío y descarte.

#### [UI3-04] La descarga puede sobrescribir cambios locales recién encolados
- **Severidad:** alta · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/notifications/data/OfflineFirstNotificationsRepository.kt:60
- **Qué está mal:** la lista de pendientes se consulta fuera de la transacción que guarda la respuesta remota. Un cambio local intercalado queda fuera de esa lista y el `upsertAll` reemplaza su proyección.
- **Escenario de fallo:** llega una respuesta que aún dice «no leído», se marca el aviso como leído antes de guardar esa respuesta y el refresco vuelve a mostrarlo sin leer aunque exista el cambio en cola.
- **Fix mínimo:** consultar y filtrar los pendientes dentro de la misma transacción que actualiza las notificaciones; añadir un test que suspenda el refresco e intercale la mutación.

#### [UI3-05] Los widgets suman solo seis vencimientos
- **Severidad:** alta · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/widgets/data/WidgetDataProvider.kt:138
- **Qué está mal:** la lista compartida se recorta a seis antes de calcular el clima, la suma semanal y la cobertura de pagos. Esos cálculos presentan el resultado como si incluyera todos los vencimientos.
- **Escenario de fallo:** con siete pagos de S/100 que vencen hoy y S/650 disponibles, el clima descuenta solo S/600 y no detecta que falta dinero para el séptimo pago.
- **Fix mínimo:** conservar todos los vencimientos para cálculos y limitar únicamente las filas dibujadas; probar siete pagos en el mismo día.

#### [NAV1-01] `ReceiptFlowViewModel` bloquea permanentemente a nuevos usuarios tras cambio de sesión (`owner` nunca se actualiza si no es nulo)
- **Severidad:** media (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable
- **Ubicación:** app/src/main/java/com/kipu/app/navigation/ReceiptFlowViewModel.kt:56, 65, 88
- **Qué está mal:** En `ReceiptFlowViewModel`, `owner` se inicializa con el `userId` actual de la sesión al crearse el ViewModel. En el bloque `init`, se observa `session.localAccess`, pero la línea 65 evalúa estrictamente `if (owner == null && access is LocalAccess.Available) owner = access.userId`. Si el usuario A cierra sesión y un usuario B inicia sesión en el mismo dispositivo, `owner` continúa siendo `"UserA"`. Como consecuencia, `hasAccess()` evalúa `(session.localAccess.value as? LocalAccess.Available)?.userId == owner`, lo que devuelve `false` (`"UserB" == "UserA"`).
- **Escenario de fallo:** El usuario A cierra sesión y el usuario B inicia sesión en el mismo teléfono. Al entrar a la bandeja de comprobantes Yape, el ViewModel queda bloqueado con el mensaje `"Desbloquea la sesión propietaria para revisar comprobantes."` y emite `candidates = emptyList()`, impidiendo que el usuario B revise, importe o registre comprobantes hasta que la aplicación sea detenida por completo y se destruya el ViewModel.
- **Fix mínimo:** En `session.localAccess.collectLatest`, cuando `access is LocalAccess.Available`, si `access.userId != owner`, resetear el estado y actualizar `owner = access.userId` en lugar de requerir que `owner == null`.
- **Verificación (Fable):** Probable: el `owner` solo se fija si era null, pero al cerrar sesión la navegación hace `popUpTo(0)`, que normalmente destruye este ViewModel.

#### [NAV1-03] `ReceiptMovementEditorHost` carece de `BackHandler` y el botón Atrás expulsa al usuario del flujo completo de comprobantes
- **Severidad:** media (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable
- **Ubicación:** app/src/main/java/com/kipu/app/navigation/ReceiptsNavigation.kt:106-121
- **Qué está mal:** Cuando el usuario revisa un comprobante confirmado o duplicado y abre el editor de movimiento (`editingMovementId != null`), `ReceiptMovementEditorHost` compone `MovementEditorRoute` como overlay sin registrar un `BackHandler`. En `ReceiptFlowScreen`, el `BackHandler` local solo está habilitado si `candidate != null || state.savedCandidate != null`. Si el usuario abrió el editor desde la bandeja o un comprobante ya registrado, ningún composable intercepta el evento Atrás, por lo que el `NavController` desapila `RECEIPT_IMPORT_ROUTE` por completo.
- **Escenario de fallo:** El usuario importa un comprobante duplicado o toca un comprobante registrado en la bandeja. Toca "Ver movimiento" para editarlo en el editor. Al estar dentro del editor, presiona la tecla o gesto Atrás del sistema esperando cerrar el editor: en lugar de cerrar el editor (`editingMovementId = null`), la navegación expulsa al usuario al dashboard o pantalla previa, abandonando la pantalla de comprobantes.
- **Fix mínimo:** En `ReceiptMovementEditorHost`, agregar `BackHandler(enabled = editingMovementId != null) { editingMovementId = null }`.
- **Verificación (Fable):** Defecto de navegación (Atrás sale del flujo), sin pérdida de datos.

#### [NAV1-05] El Snackbar "Comprobante agregado" se cancela instantáneamente al consumir su propia clave en `LaunchedEffect`
- **Severidad:** media (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/navigation/ReceiptsNavigation.kt:165-170
- **Qué está mal:** En `ReceiptFlowScreen`, se observa el evento de comprobante agregado mediante `LaunchedEffect(state.addedEventId)`. La primera instrucción dentro del bloque es `viewModel.consumeAddedEvent()`, la cual muta de inmediato el estado del ViewModel poniendo `addedEventId = null`. En Compose, mutar la clave sobre la que se ejecuta el `LaunchedEffect` cancela de forma inmediata la corrutina en ejecución. Por tanto, `snackbar.showSnackbar("Comprobante agregado", "Ver", ...)` es cancelado antes de que el snackbar llegue a dibujarse o pueda ser pulsado.
- **Escenario de fallo:** El usuario escanea o importa un comprobante Yape exitosamente. El ViewModel genera `addedEventId = id`. La UI inicia `LaunchedEffect`, consume el evento inmediatamente, lo que cancela la corrutina del Snackbar. El usuario nunca ve la notificación "Comprobante agregado" ni puede pulsar la acción "Ver" para abrir el comprobante recién agregado.
- **Fix mínimo:** Consumir el evento `viewModel.consumeAddedEvent()` después de que `snackbar.showSnackbar` retorne su resultado, evitando cancelar la corrutina en vuelo.
- **Verificación (Fable):** Confirmado: `consumeAddedEvent()` cambia la clave del `LaunchedEffect`, que se reinicia y cancela el `showSnackbar`. Defecto de UX.

#### [NAV1-06] `ThemeTransition` retiene la `Activity` y un `Bitmap` de pantalla completa en memoria estática sin reciclaje explícito
- **Severidad:** media · **Categoría:** escala · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/ui/theme/ThemeTransition.kt:31, 44, 72-73, 91-96
- **Qué está mal:** `ThemeTransition` es un objeto singleton (`object`). En `show()`, guarda una referencia a un `ImageView(activity)` que contiene un `Bitmap` ARGB_8888 del tamaño completo de la pantalla (~18 MB en pantallas 1440x3120). En `clear()`, se remueve la vista y se anula `overlay`, pero nunca se invoca `bitmap.recycle()`. Además, si la actividad se recrea durante la transición o el cambio de tema, la referencia estática a `ImageView` retiene la instancia destruida de `Activity` hasta el timeout de seguridad (1.2s) o indefinidamente si ocurre una excepción previa.
- **Escenario de fallo:** En dispositivos con pantallas QHD+ o memoria RAM ajustada (2–3 GB), cambios repetidos de tema o rotación durante la transición provocan retención de bitmaps de 18 MB en la memoria nativa, disparando pausas de Garbage Collector (jank) o riesgo de `OutOfMemoryError`.
- **Fix mínimo:** En `clear()`, extraer el drawable/bitmap del `ImageView` antes de removerlo y llamar a `recycle()`. Anular las referencias inmediatamente y no retener `ImageView` en variables estáticas más allá del frame de fade.

#### [NAV1-07] `KipuSystemBars` re-ejecuta `activity.enableEdgeToEdge()` en cada recomposición dentro de un `SideEffect`
- **Severidad:** media · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/ui/theme/KipuSystemBars.kt:46-59
- **Qué está mal:** `KipuSystemBars` se invoca en la raíz de `KipuTheme`. En su interior, `SideEffect` ejecuta `activity.enableEdgeToEdge(...)` en cada frame en que `KipuTheme` se recompone. `enableEdgeToEdge()` es una operación de ciclo de vida de la Activity diseñada para llamarse una sola vez en `Activity.onCreate()`; re-ejecutarla en cada recomposición de Compose reinstala listeners de insets, recrea estilos del sistema y compite contra el `WindowInsetsControllerCompat` que `MainActivity.kt:304` configura por su cuenta.
- **Escenario de fallo:** Cada vez que cambia un estado global o se anima la UI dentro de `KipuTheme`, `SideEffect` llama a `enableEdgeToEdge()`, forzando al framework de Android a re-evaluar los insets de la ventana y los estilos del sistema, generando trabajo redundante en el hilo de UI.
- **Fix mínimo:** Controlar los system bars en Compose utilizando `WindowCompat.getInsetsController()` solo cuando el valor de `darkTheme` cambie (mediante `LaunchedEffect(darkTheme)` o `DisposableEffect`), sin volver a invocar `activity.enableEdgeToEdge()`.

#### [NAV1-08] `KipuSkeletonBlock` lee `.value` de animación en la fase de composición, invalidando y recomponiendo la UI a 60-120 fps
- **Severidad:** media · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/ui/component/KipuStates.kt:69-79
- **Qué está mal:** En `KipuSkeletonBlock`, `alpha` se extrae llamando a `.value` en el cuerpo del composable: `val alpha = ...animateFloat(...).value`. Luego se pasa a `graphicsLayer { this.alpha = alpha }`. Leer `.value` durante la fase de composición suscribe al composable a los cambios del `State<Float>`, obligando a Compose a recomponer el bloque esqueleto completo en cada frame del pulso (60 o 120 veces por segundo).
- **Escenario de fallo:** En pantallas que muestran listas de esqueletos de carga (por ejemplo, el historial de movimientos o el dashboard con múltiples cuentas y tarjetas cargando), cada bloque esqueleto se recompone 60-120 veces por segundo, disparando el uso de CPU y consumiendo batería innecesariamente.
- **Fix mínimo:** Retener la referencia `val alphaState = transition.animateFloat(...)` y leer `alphaState.value` únicamente dentro de la lambda diferida de `graphicsLayer { this.alpha = alphaState.value }`.

#### [NAV1-09] `GoalsNavigation` accede a `ACCOUNTS_DASHBOARD_ROUTE` con `getBackStackEntry` sin verificar su presencia en la pila
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/navigation/GoalsNavigation.kt:38-39, 63-64
- **Qué está mal:** En las rutas `GOAL_CREATE_ROUTE` y `GOAL_EDIT_ROUTE`, se ejecuta `val ownerEntry = remember(backStackEntry) { navController.getBackStackEntry(ACCOUNTS_DASHBOARD_ROUTE) }` sin comprobar si `ACCOUNTS_DASHBOARD_ROUTE` existe en la pila. En contraste con `AccountsNavigation.kt:150-154` (que utiliza `previousBackStackEntry?.takeIf { ... } ?: hiltViewModel(backStackEntry)`), si la ruta se abre desde un deep link frío, notificación o tras una limpieza de pila, `getBackStackEntry` lanza `IllegalArgumentException` no capturada.
- **Escenario de fallo:** Si una meta se abre para edición desde un flujo donde el dashboard no está en el backstack, la llamada a `navController.getBackStackEntry(ACCOUNTS_DASHBOARD_ROUTE)` lanza `IllegalArgumentException: No destination with route accounts/dashboard is on the NavController's back stack` y la app crashea.
- **Fix mínimo:** Usar `runCatching { navController.getBackStackEntry(ACCOUNTS_DASHBOARD_ROUTE) }.getOrNull()` y si es nulo obtener el ViewModel desde `backStackEntry`.

#### [NAV1-10] `DebtsNavigation` no provee interfaz de navegación ni acción de retorno cuando la deuda no existe (`state.debt == null`)
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/navigation/DebtsNavigation.kt:176-180, 248-252
- **Qué está mal:** En `DEBT_DETAIL_ROUTE` y `DEBT_SETTLE_ROUTE`, cuando `state.debt == null` y no está cargando, se emite un simple `Text(state.errorMessage ?: "No se encontró esta deuda.", ...)` sin estar contenido en un `Scaffold`, sin `TopAppBar` y sin botón de volver. Si el usuario llegó a esta ruta desde una notificación de recordatorio (donde `MainActivity.kt:656-660` limpió la pila con `popUpTo(0)`), la pantalla queda en un callejón sin salida (deadlock de navegación) donde la única salida es pulsar el botón Atrás del sistema que cierra la app.
- **Escenario de fallo:** Usuario abre una notificación de recordatorio para una deuda que fue cancelada o eliminada en otro dispositivo sincronizado. La pantalla muestra un texto rojo en la esquina superior izquierda sobre fondo por defecto sin barra de navegación ni botón de retorno. Al presionar Atrás del sistema, la app se cierra.
- **Fix mínimo:** Envolver el estado de error en un `Scaffold` con botón de navegación hacia atrás (`onNavigateBack = { if (!navController.popBackStack()) navController.navigate(ACCOUNTS_DASHBOARD_ROUTE) }`) o mostrar `KipuEmptyState`.

#### [NAV1-11] `CaptureNavigation` pierde el mensaje de confirmación (`leave`) cuando se accede a un candidato mediante deep link frío
- **Severidad:** media · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/navigation/CaptureNavigation.kt:76-84
- **Qué está mal:** En `CAPTURE_CANDIDATE_ROUTE`, la función `leave` ejecuta: `navController.previousBackStackEntry?.savedStateHandle?.set(CAPTURE_MESSAGE_KEY, message)`. Luego evalúa `if (!navController.popBackStack()) { navController.navigate(CAPTURE_INBOX_ROUTE) { popUpTo(CAPTURE_CANDIDATE_ROUTE) { inclusive = true } } }`. Si el usuario ingresó por deep link (`kipu://capture/candidate/{id}`), `previousBackStackEntry` es `null`. El mensaje nunca se guarda en el savedStateHandle de la bandeja y se pierde silenciosamente.
- **Escenario de fallo:** Usuario pulsa una notificación de comprobante capturado por OCR (`kipu://capture/candidate/{id}`). Revisa el comprobante y confirma el registro. La función `leave` intenta escribir el mensaje en `previousBackStackEntry` (que es nulo), luego abre la bandeja. La bandeja se muestra sin el banner de feedback "Movimiento registrado".
- **Fix mínimo:** Si `!navController.popBackStack()`, pasar el mensaje al `currentBackStackEntry` de la nueva navegación: `navController.currentBackStackEntry?.savedStateHandle?.set(CAPTURE_MESSAGE_KEY, message)`.

#### [NAV1-12] `SettingsNavigation` redirige a `MOVEMENTS_HISTORY_ROUTE` en lugar de `ACCOUNTS_DASHBOARD_ROUTE` al finalizar onboarding biométrico
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/navigation/SettingsNavigation.kt:37-40
- **Qué está mal:** Al completar el paso biométrico del onboarding (`BIOMETRIC_ROUTE`), la acción de continuar navega a `MOVEMENTS_HISTORY_ROUTE` con `popUpTo(PLAN_SELECTION_ROUTE) { inclusive = true }`. Como `ACCOUNTS_DASHBOARD_ROUTE` nunca se incluyó en la pila, `movements/history` se convierte en la raíz del backstack en vez del dashboard. Cuando el usuario pulsa luego "Dinero" en la barra inferior, el dashboard se apila encima de `movements/history`, impidiendo salir de la app con el botón Atrás desde "Dinero".
- **Escenario de fallo:** Usuario nuevo finaliza el onboarding biométrico. Aparece la pantalla de historial de movimientos. Toca "Dinero" en la barra inferior. En lugar de estar en el inicio canónico, ahora el botón Atrás del sistema lo regresa a "Movimientos" en vez de salir de la app.
- **Fix mínimo:** Cambiar la navegación de finalización de onboarding a `ACCOUNTS_DASHBOARD_ROUTE` con `backStackEntry.savedStateHandle[ROOT_TAB_KEY] = RootTab.MONEY`.

#### [NAV1-13] `DatePickerDialog` y `TimePickerDialog` nativos de Android en `ReceiptsNavigation` se destruyen y pueden fugar ventana ante cambios de configuración
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/navigation/ReceiptsNavigation.kt:495-498, 509-512
- **Qué está mal:** En `ReceiptMetadataEditor`, para editar la fecha y hora de un comprobante se instancian directamente diálogos del framework clásico (`android.app.DatePickerDialog(context, ...).show()` y `android.app.TimePickerDialog(context, ...).show()`), pasando el `Context` de la composición. Estos diálogos no sobreviven a la recreación de la Activity (por rotación o cambio de tema), provocando posibles excepciones de ventana huérfana (`BadTokenException`) o pérdida del estado en edición, además de no respetar el tema visual Material 3 de Kipu.
- **Escenario de fallo:** Usuario está revisando un comprobante en `ReceiptsNavigation` y abre el diálogo de fecha. Mientras el diálogo está abierto, el usuario gira el teléfono o cambia el modo oscuro del sistema. El diálogo desaparece, se pierde la selección y se fuga el contexto de la Activity anterior en los logs de Android (`Activity has leaked window`).
- **Fix mínimo:** Utilizar componentes Compose como `KipuOptionalDateField` / `androidx.compose.material3.DatePickerDialog` que manejan su ciclo de vida y recomposición de manera reactiva y segura.

#### [UI1-01] `AnalyticsPeriod.previous()` descarta días al comparar meses completos cerrados más cortos con meses anteriores más largos (Feb -> Ene, Abr -> Mar)
- **Severidad:** media (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/analytics/domain/Analytics.kt:23-28
- **Qué está mal:** En `AnalyticsPeriod.previous()`, cuando `start.dayOfMonth == 1`, calcula `val span = ChronoUnit.DAYS.between(start, endInclusive)` y `val previousEnd = minOf(previousStart.plusDays(span), previousStart.withDayOfMonth(previousStart.lengthOfMonth()))`. Si el periodo analizado es un mes cerrado completo que tiene menos días que el mes anterior (por ejemplo, Febrero tiene 28 días y Enero tiene 31; o Abril tiene 30 y Marzo 31), `span` es 27 días (28 - 1). `previousStart.plusDays(27)` da el 28 de enero. La función `minOf` selecciona el 28 de enero. Como resultado, la comparación de todo Febrero ignora el 29, 30 y 31 de Enero (3 días completos de gastos). De igual forma, comparar Abril (30 días) contra Marzo (31 días) ignora el 31 de Marzo.
- **Escenario de fallo:** Usuario en plan Premium analiza o compara Febrero 2026 completo (mes cerrado, 2026-02-01 a 2026-02-28). Llama a `previous()`. El periodo retornado es `2026-01-01 a 2026-01-28`. Cualquier gasto realizado el 29, 30 o 31 de enero queda excluido de la comparación previa, distorsionando el total del mes anterior y el porcentaje de cambio reportado.
- **Fix mínimo:** Si el mes actual está cerrado (`endInclusive == start.withDayOfMonth(start.lengthOfMonth())`), `previousEnd` debe ser el último día del mes anterior (`previousStart.withDayOfMonth(previousStart.lengthOfMonth())`). Solo si es un mes parcial (`isPartial`) debe aplicarse `minOf(previousStart.plusDays(span), ...)`.
- **Verificación (Fable):** Febrero cerrado (span 27) compara con 1–28 de enero; el comentario del código justifica el recorte solo para meses en curso.

#### [UI1-03] `AnalyticsHeader` coloca `HorizontalDivider` al tope de la pantalla solapando el header
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsScreen.kt:381-421
- **Qué está mal:** `AnalyticsHeader` emite dos composables raíz independientes: un `Row` con el header y un `HorizontalDivider(color = colors.borderSubtle)` sin estar envueltos en un `Column`. Al ser pasado a `Scaffold(topBar = { AnalyticsHeader(...) })`, Compose Scaffold mide ambos placeables de `topBar` y los posiciona en `(0, 0)`. Como consecuencia, el divider horizontal no se dibuja debajo del header, sino en Y=0 (al tope absoluto de la barra de estado o encabezado).
- **Escenario de fallo:** Al renderizar `AnalyticsScreen`, la línea divisoria inferior del encabezado no aparece separando el header del contenido scrolleable, sino superpuesta en la parte superior del status bar / app bar.
- **Fix mínimo:** Envolver el `Row` y el `HorizontalDivider` dentro de un `Column(Modifier.fillMaxWidth())`.

#### [UI1-04] El gráfico de saldo diario (`BalanceLineSection`) ignora los filtros de cuenta activos
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsScreen.kt:533-536
- **Qué está mal:** Cuando el usuario filtra por una cuenta específica (ej. Cuenta BCP), `PeriodSubline` muestra "Filtros (1)" y los KPIs/semanas/categorías muestran los gastos de esa cuenta. Sin embargo, el bloque de saldo diario construye `ids` como `overview.accountsIn(currency).map { it.account.id.value }.toSet()` y pasa `overview.total(currency)`. Ignora por completo `ui.filters.accountIds`. Por tanto, el gráfico muestra la evolución patrimonial y el saldo conjunto de TODAS las cuentas líquidas del usuario, contradiciendo el filtro seleccionado.
- **Escenario de fallo:** Usuario con dos cuentas (A con S/ 5,000 y B con S/ 100). Filtra por la cuenta B para auditar sus movimientos. El gráfico de saldo diario muestra S/ 5,100 y la curva combinada de ambas cuentas en vez de los S/ 100 de la cuenta B.
- **Fix mínimo:** En `AnalyticsScreen.kt:534`, si `ui.filters.accountIds.isNotEmpty()`, filtrar los IDs y sumar solo los saldos de las cuentas incluidas en el filtro.

#### [UI1-05] `BalanceSeriesCalculator.series` ejecuta algoritmo O(D * N) en el hilo principal durante la recomposición
- **Severidad:** media · **Categoría:** escala · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/analytics/domain/AnalyticsOverview.kt:20-28 y app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsScreen.kt:533-536
- **Qué está mal:** Para cada día del periodo analizado (D = 28..31 días), `BalanceSeriesCalculator.series` recorre la lista completa de `deltas` con `.filter { it.atMillis >= endOfDay }.sumOf { it.deltaMinor }`. Con miles de movimientos de usuario (N deltas acumulados desde el inicio del periodo hasta hoy), esto ejecuta 31 * N operaciones lineales dentro de un bloque `remember` en el hilo de UI de Compose.
- **Escenario de fallo:** Un usuario con 5,000 movimientos acumulados cambia de tab o rota la pantalla. En el hilo principal de renderizado se realizan ~150,000 iteraciones y cálculos de fecha de forma síncrona, provocando jank y pérdida de frames (lag perceptible).
- **Fix mínimo:** Calcular la serie en un solo pase acumulativo inverso O(D + N) ordenando/agrupando deltas por día, o precomputarlo en background en el ViewModel/UseCase antes de pasarlo al estado de la UI.

#### [UI1-06] `AnalyticsDao.observeLedgerRows` realiza un escaneo completo sin índice al filtrar con expresión `COALESCE`
- **Severidad:** media · **Categoría:** escala · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/analytics/data/AnalyticsDao.kt:118-123
- **Qué está mal:** La consulta `observeLedgerRows` filtra con `WHERE le.user_id = :owner AND COALESCE(t.occurred_at, le.created_at) >= :fromMillis`. Dado que `COALESCE` combina columnas de dos tablas en un LEFT JOIN, SQLite no puede utilizar ningún índice B-Tree (`index_ledger_entries_user_id_account_id` ni `index_transactions_user_id_occurred_at`). Realiza un escaneo de tabla completo de `ledger_entries` para el usuario y hace join con `transactions` para cada fila histórica.
- **Escenario de fallo:** Con 10,000 movimientos en la base de datos local, cada vez que el usuario abre Análisis o cambia de mes, la consulta lee e inspecciona las 10,000 filas de libro mayor sin filtrado indexado.
- **Fix mínimo:** Almacenar la fecha efectiva/occurred_at directamente en `ledger_entries` (o filtrar por `le.created_at >= :fromMillis` si las fechas son aproximadas) para aprovechar el índice por fecha.

#### [UI1-07] `AnalyticsViewModel` crashea con `Periodo inválido` si un periodo usa una zona horaria distinta a `ZoneId.systemDefault()`
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsViewModel.kt:86
- **Qué está mal:** `report()` impone `require(period.zone == zone && !period.endInclusive.isAfter(today)) { "Periodo inválido" }`, donde `zone = ZoneId.systemDefault()`. Si un caller inyecta o construye un periodo con la zona de negocio de Kipu (`ZoneId.of("America/Lima")`) o si cambia la zona del sistema mientras la pantalla o el ViewModel están activos, la aserción falla lanzando `IllegalArgumentException`, que es capturada por el catch de la Flow emitiendo `AnalyticsUiState.Error`.
- **Escenario de fallo:** Una prueba unitaria, deeplink o componente que configure el periodo usando `America/Lima` (la zona oficial de Kipu) en un dispositivo cuya zona sea distinta de UTC-5 provoca que la pantalla muestre inmediatamente "No se pudieron cargar las estadísticas".
- **Fix mínimo:** Eliminar la restricción de igualdad estricta de objeto `period.zone == zone` (la conversión de epoch millis ya encapsula la zona del periodo) o normalizar el periodo recibido a la zona de negocio antes de consultar.

#### [UI1-08] `Currency.fromCode` no controlado en `ObserveNetWorthSummary` ante códigos de moneda corruptos o no soportados
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/home/domain/ObserveNetWorthSummary.kt:31
- **Qué está mal:** En `ObserveNetWorthSummary.kt:31`, se hace `Currency.fromCode(it.currencyCode)`. Si `debtRepository` devuelve una deuda con un código de moneda inesperado o en minúsculas no estándar (distinto de "PEN" o "USD"), `Currency.fromCode` lanza `IllegalArgumentException("Unsupported currency code: ...")`. A diferencia de `ObserveAnalyticsOverview`, `ObserveNetWorthSummary` no usa `runCatching` localmente y provoca que el Flow de patrimonio falle completamente.
- **Escenario de fallo:** Una sincronización remota o registro corrupto en la tabla de deudas con `currency_code = 'EUR'` hace que el inicio de la app muestre un error permanente "No se pudo calcular tu patrimonio".
- **Fix mínimo:** Usar `runCatching { Currency.fromCode(it.currencyCode) }.getOrNull()` y filtrar los que sean nulos.

#### [UI1-09] `ObserveAnalyticsOverview` carece totalmente de pruebas unitarias
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/analytics/domain/ObserveAnalyticsOverview.kt:31-81
- **Qué está mal:** `ObserveAnalyticsOverview` es un caso de uso central que combina 5 fuentes (dashboard de instrumentos, metas activas con eventos, vencimientos de deudas y tarjetas, deudas principales y deltas de libro mayor). Contiene lógica financiera compleja de selección de mejor meta por progreso, filtrado de vencimientos por fin de mes y acumulación de saldos de préstamos por moneda. No tiene ningún test unitario en el repositorio (solo se mockea en `AnalyticsViewModelTest`).
- **Escenario de fallo:** Un cambio o regresión en el filtrado de `duesPart` (`it.dueDate <= endOfMonth`) o en la selección de metas pasa desapercibido en la suite de pruebas automatizadas. - Test mínimo que falta: Un test unitario `ObserveAnalyticsOverviewTest` con repositorios simulados que valide que: 1) deudas por pagar vencidas o del mes se suman a `upcoming`; 2) metas inactivas se excluyen y la meta activa con mayor porcentaje se selecciona como snapshot.
- **Fix mínimo:** —

#### [UI1-10] `AnalyticsDao` no tiene pruebas para `observeIncomes`, `observeLedgerRows` ni opciones de filtros
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/analytics/AnalyticsDaoTest.kt:15
- **Qué está mal:** `AnalyticsDaoTest.kt` solo prueba la consulta `observeExpenses`. Las consultas `observeIncomes` (con su compleja lista de exclusión de kinds), `observeLedgerRows` (con su LEFT JOIN y COALESCE), `observeCategoryOptions`, `observeAccountOptions`, `observeCardOptions` y `observeMerchantOptions` no tienen ningún test instrumentado de base de datos.
- **Escenario de fallo:** Si se introduce una errata en la consulta SQL de `observeIncomes` o una exclusión errónea de `legacy_kind`, ningún test falla. - Test mínimo que falta: En `AnalyticsDaoTest`, insertar transacciones de tipo `INCOME` con distintos `operation_kind` y `legacy_kind` (ej. REFUND vs STANDARD vs OPENING) y verificar que `observeIncomes` solo retorne ingresos operativos.
- **Fix mínimo:** —

#### [UI1-11] Asimetría de sesión entre `ObserveFinancialDashboard` (sin owner) y `observeDebts(userId)` en `ObserveNetWorthSummary`
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/home/domain/ObserveNetWorthSummary.kt:22-23
- **Qué está mal:** `ObserveNetWorthSummary.invoke(userId: String)` combina `observeFinancialDashboard()` (que no acepta `userId` y depende del estado global de sesión de `OfflineFirstFinancialInstrumentsRepository`) con `debtRepository.observeDebts(userId)` (que sí filtra explícitamente por el parámetro `userId`). Durante una transición de cuenta de usuario, ambas fuentes pueden emitir en momentos distintos, existiendo una ventana donde el dashboard pertenece al usuario anterior mientras las deudas pertenecen al nuevo usuario.
- **Escenario de fallo:** En un cambio de usuario rápido en el mismo dispositivo, el flujo emite transitoriamente un `NetWorthSummary` que mezcla los saldos bancarios del usuario previo con las deudas del nuevo usuario.
- **Fix mínimo:** Hacer que `ObserveFinancialDashboard` acepte `userId` explícito como todas las demás funciones de consulta del dominio Kipu.
- **Cross-módulo:** sí → ACC1

#### [UI2-03] Carrera de estados en `PlanPurchaseViewModel` permite compras concurrentes en Google Play
- **Severidad:** media (el worker dijo alta) · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable
- **Ubicación:** app/src/main/java/com/kipu/app/feature/plans/presentation/PlanPurchaseViewModel.kt:30-36, 44-55
- **Qué está mal:** En `PlanPurchaseViewModel.selectOffer(productId)`, la función actualiza el estado directamente: `state.copy(selectedProductId = productId, status = if (state.effectivePremium) PlanPurchaseStatus.VERIFIED else PlanPurchaseStatus.READY, errorCode = null)` Si un proceso de compra o verificación ya está en vuelo (`status == LAUNCHING` o `VERIFYING`), las tarjetas de oferta en `PlanPurchaseScreen` no deshabilitan su `onClick`. Al tocar otra tarjeta, `status` se reinicia a `READY`. Esto reactiva de inmediato el botón principal `purchase-cta` (`enabled = state.status !in setOf(PlanPurchaseStatus.LAUNCHING, PlanPurchaseStatus.VERIFYING)`). Si el usuario pulsa el botón de nuevo, `startPurchase` supera la validación y lanza una segunda llamada simultánea a `repository.startPurchase(activity, offer)` contra el `BillingClient` de Google Play, lo que desencadena excepciones de estado ilegal (`IllegalStateException`) o transacciones duplicadas.
- **Escenario de fallo:** Usuario pulsa "Comprar" en el plan anual. Mientras la pasarela de Google Play Billing se inicializa (`status = LAUNCHING`), el usuario toca la tarjeta del plan mensual. El estado pasa inmediatamente a `READY`. El botón "Comprar" vuelve a quedar activo; el usuario lo vuelve a presionar. Se invocan dos flujos de facturación concurrentes sobre la misma Activity.
- **Fix mínimo:** En `selectOffer`, no mutar `status` si `state.status == PlanPurchaseStatus.LAUNCHING || state.status == PlanPurchaseStatus.VERIFYING`.
- **Verificación (Fable):** Cambiar de oferta reinicia el estado a READY mientras otra compra está en vuelo; Play suele impedir dos flujos simultáneos.

#### [UI2-05] Vulnerabilidad de seguridad: `toggleLocalUnlock` desactiva la protección biométrica sin solicitar autenticación previa
- **Severidad:** media (el worker dijo alta) · **Categoría:** seguridad · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/feature/settings/presentation/BiometricSettingsViewModel.kt:73-82
- **Qué está mal:** Para habilitar el bloqueo biométrico, `toggleLocalUnlock` exige autenticación vía `gateway.authenticate(activity, ...)` con biometría o PIN. Sin embargo, para desactivarlo (`currentEnabled == true`), la función apaga directamente la protección en Room y en memoria sin requerir autenticación alguna: `lockCoordinator.setLocalUnlockEnabled(userId, false)` Cualquier persona que acceda al teléfono desbloqueado por unos segundos puede ingresar a Ajustes > Bloqueo local y desactivar permanentemente la protección biométrica con un solo toque, vulnerando la privacidad de todos los datos financieros.
- **Escenario de fallo:** Un usuario presta su teléfono para ver una foto o hacer una llamada. El tercero abre Kipu (que en ese momento no ha bloqueado por timeout de 60s), entra a Ajustes > Bloqueo local y toca el switch. La protección se desactiva instantáneamente sin pedir confirmación de huella ni PIN del dispositivo.
- **Fix mínimo:** Requerir `gateway.authenticate` también cuando `currentEnabled == true` antes de llamar a `lockCoordinator.setLocalUnlockEnabled(userId, false)`.
- **Verificación (Fable):** Confirmado: desactivar el bloqueo no pide autenticación. Baja a media: exige tener la app ya desbloqueada en la mano.

#### [UI2-06] Mutaciones concurrentes y encolamiento duplicado en WorkManager al alternar `hideBalances` en `ProfileSettingsScreen`
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/settings/presentation/ProfileSettingsScreen.kt:426-429
- **Qué está mal:** En `ProfileSettingsScreen.kt`, al presionar el switch "Ocultar saldos e importes", el callback ejecuta: `onCheckedChange = { viewModel.toggleHideBalances(); viewModel.savePreferences() }` `toggleHideBalances()` lanza una corrutina que actualiza el perfil en Room con `setHideBalances` y programa un trabajo único en WorkManager con `profileSyncScheduler.scheduleSync(userId)`. Inmediatamente después, `savePreferences()` lanza OTRA corrutina que guarda el perfil con `updatePreferences` y vuelve a programar WorkManager con `ExistingWorkPolicy.REPLACE`. Como `toggleHideBalances()` no utiliza `saveMutex`, ambas corrutinas acceden a Room en paralelo. Además, `ProfilePreferenceDelta` en `savePreferences()` ni siquiera propaga `hideBalances`, por lo que la segunda llamada es un guardado redundante y duplicado que compite por la base de datos.
- **Escenario de fallo:** Usuario cambia el switch de ocultar saldos. Dos corrutinas separadas disparan transacciones Room simultáneas y dos encolamientos consecutivos en WorkManager, generando escrituras de outbox redundantes.
- **Fix mínimo:** Eliminar la llamada `viewModel.savePreferences()` dentro del `onCheckedChange` de la línea 428; `toggleHideBalances()` ya realiza la persistencia completa.

#### [UI2-07] Carrera de hilos por variable no sincronizada `pendingSaveRequests` en `SettingsViewModel`
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/settings/presentation/SettingsViewModel.kt:74, 182, 200, 204, 209
- **Qué está mal:** `pendingSaveRequests` es un campo mutable primitivo `private var pendingSaveRequests = 0` sin `@Volatile` ni sincronización atómica. En `savePreferences()`, se incrementa fuera del lock (`pendingSaveRequests += 1`) en el hilo de llamada de la UI, mientras que dentro de la corrutina en background se decrementa dentro de `saveMutex.withLock` (`pendingSaveRequests -= 1`). Ante eventos rápidos consecutivos (por ejemplo, pulsaciones rápidas del stepper de corte contable `stepMonthStart`), los accesos multihilo no atómicos provocan lecturas y escrituras desincronizadas, lo que puede causar que `pendingSaveRequests` quede en un valor mayor a cero y mantenga permanentemente `isSaving = true` en la UI.
- **Escenario de fallo:** El usuario presiona rápidamente varias veces el botón `+` para ajustar el día de inicio contable. La carrera de datos deja `pendingSaveRequests` con valor 1 tras completarse todas las escrituras, dejando `isSaving = true` indefinidamente y deshabilitando el botón de refresco de plan de pruebas.
- **Fix mínimo:** Mover el incremento de `pendingSaveRequests` al interior de `saveMutex.withLock` o tipar la variable como `AtomicInteger`.

#### [UI2-08] Posible pérdida de evento único `Confirmed` en `PlanSelectionRoute` ante recreación del ciclo de vida
- **Severidad:** media · **Categoría:** concurrencia · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/plans/presentation/PlanSelectionViewModel.kt:27-28 y app/src/main/java/com/kipu/app/feature/plans/presentation/PlanSelectionRoute.kt:14-20
- **Qué está mal:** `PlanSelectionViewModel` expone sus eventos mediante `private val eventChannel = Channel<PlanSelectionEvent>(Channel.BUFFERED)` y `val events = eventChannel.receiveAsFlow()`. En `PlanSelectionRoute`, el colector usa `repeatOnLifecycle(Lifecycle.State.STARTED)`. Si la confirmación ocurre en el instante exacto en que la Activity entra en `STOPPED` (por ejemplo, el usuario rota la pantalla o entra una llamada en el momento de confirmar), el colector se cancela. Al reanudarse en `STARTED`, `receiveAsFlow()` no preserva el evento si el canal fue recreado o si se suscribe un nuevo colector en una recomposición estructural, perdiéndose la señal de navegación.
- **Escenario de fallo:** El usuario confirma el plan y gira el dispositivo inmediatamente. El evento de confirmación se emite durante la transición de ciclo de vida y no es recibido tras el reinicio, dejando al usuario congelado en la pantalla de selección a pesar de haber confirmado su elección.
- **Fix mínimo:** Sustituir `Channel.receiveAsFlow()` por un `MutableSharedFlow<PlanSelectionEvent>(extraBufferCapacity = 1)` que no pierda eventos durante cambios de recolector.

#### [UI2-09] `SetupDraftStore.bindOwner` descarta el correo del usuario si se invoca con owner previo
- **Severidad:** media · **Categoría:** datos · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/setup/SetupDraft.kt:89-93
- **Qué está mal:** La función `bindOwner` implementa: `fun bindOwner(owner: String) { if (ownerId == owner) return; _draft.value = SetupDraft(email = if (ownerId == null) _draft.value.email else ""); ownerId = owner }` Si por alguna razón la sesión ya tenía un `ownerId` registrado (por ejemplo, al inicializar `SetupViewModel.init` con un usuario previo o al verificar la cuenta), cualquier llamada subsecuente a `bindOwner` borra el campo `email` dejándolo en blanco (`""`). Posteriormente, en `SetupViewModel.confirmAll()`: `curtain.start(store.draft.value.email, registering = true)` Se pasa un correo vacío a `SessionEntryCurtain`, afectando la presentación del saludo inicial.
- **Escenario de fallo:** Un usuario que inició sesión previamente se registra con una nueva cuenta; al verificarse el OTP e invocarse `store.bindOwner(userId)`, el borrador descarta el email y la cortina de bienvenida posterior muestra un texto en blanco sin dirección de correo.
- **Fix mínimo:** Preservar el correo si el borrador ya lo contenía: `_draft.value = SetupDraft(email = _draft.value.email)`.

#### [UI2-10] Cast directo `context as? FragmentActivity` en `BiometricSettingsScreen` falla silenciosamente ante `ContextWrapper`
- **Severidad:** media · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/settings/presentation/BiometricSettingsScreen.kt:124-126
- **Qué está mal:** En `BiometricSettingsScreen.kt`, al presionar el switch se obtiene la Activity con: `val activity = context as? FragmentActivity` `if (activity != null) viewModel.toggleLocalUnlock(activity)` En Jetpack Compose, `LocalContext.current` es comúnmente una instancia de `ContextThemeWrapper`, `TintContextWrapper` o wrappers inyectados por temas/Hilt. Un cast directo `as? FragmentActivity` evalúa a `null`. En ese caso, la pulsación del switch no produce ninguna acción ni mensaje de error. En `PlanPurchaseRoute.kt:41-45` se implementó correctamente una función de desenredo recursivo `findActivity()`, pero se omitió en esta pantalla.
- **Escenario de fallo:** En dispositivos donde el contexto es envuelto por el tema de Compose o inyección de recursos, el usuario pulsa para activar el bloqueo biométrico y el switch no responde ni lanza el prompt biométrico.
- **Fix mínimo:** Usar una función recursiva de desenredo: `private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) { is FragmentActivity -> this; is ContextWrapper -> baseContext.findFragmentActivity(); else -> null }`.

#### [UI2-11] Control fantasma en `ProfileSettingsScreen`: "Alertas de vencimiento" es texto estático sin modelo subyacente
- **Severidad:** media · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/settings/presentation/ProfileSettingsScreen.kt:439-472
- **Qué está mal:** La tarjeta "Alertas de vencimiento - Días de anticipación para recordatorios" muestra un badge con "3 días" completamente cableado en código estático. No tiene `clickable`, no responde a toques, y no existe ningún campo en `UserProfile`, `UserProfileCacheEntity`, ni `SettingsUiState` para almacenar o modificar esta preferencia. Es un control ficticio que confunde al usuario haciéndole creer que puede configurar sus alertas.
- **Escenario de fallo:** El usuario intenta cambiar los días de anticipación de sus recordatorios de vencimiento; toca la tarjeta repetidamente y no reacciona, generando frustración y reportes de bug de UI.
- **Fix mínimo:** Eliminar la tarjeta hasta que el modelo de datos de preferencias soporte la configuración de alertas de vencimiento, o enlazarla a la pantalla correspondiente si pertenecía a Notificaciones (`UI3`).
- **Cross-módulo:** sí → UI3
- **Ahorro estimado:** 34 líneas.

#### [UI2-12] Omisión generalizada de externalización i18n en pantallas de ajustes, permisos y biometría
- **Severidad:** media · **Categoría:** escala · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/settings/presentation/PermissionsScreen.kt:125, 171, 249, 257, 348, 378, 390, 400; ProfileSettingsScreen.kt:150, 184, 255, 329, 421, 533; BiometricSettingsScreen.kt:87, 166, 172, 203, 205, 237, 256, 262
- **Qué está mal:** A diferencia de `PlanPurchaseScreen.kt` y `PlanSelectionScreen.kt` que usan rigurosamente `stringResource(R.string...)`, los módulos de `PermissionsScreen`, `ProfileSettingsScreen` y `BiometricSettingsScreen` tienen decenas de textos de interfaz de usuario hardcodeados en español ("Ajustes", "Bloqueo Local", "Permisos y Automatización", "Fuentes Disponibles", "Aceptar y Continuar", "Cerrar", "Explicación", "Revocar", "Habilitar", "Abrir ajustes", "Cerrar sesión", etc.). Esto impide la localización a otros idiomas o la personalización de textos según lineamientos del sistema.
- **Escenario de fallo:** Si se despliega la app en inglés u otro idioma en Google Play, las pantallas de ajustes, permisos y biometría permanecen completamente en español.
- **Fix mínimo:** Extraer todas las cadenas literales a `res/values/strings.xml` y consumirlas mediante `stringResource(R.string...)`.

#### [UI2-13] Falta de pruebas para la resolución de conflictos remotos en `OfflineFirstProfilePreferencesRepository`
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/feature/settings/data/OfflineFirstProfilePreferencesRepositoryTest.kt:64-71
- **Qué está mal:** El test existente `a row already in conflict is not merged into` solo valida que una fila en `CONFLICT` no se fusione con una nueva fila `PENDING`. No existe ninguna prueba que verifique qué ocurre cuando el worker recibe un `CONFLICT` desde el backend Supabase, ni cómo el repositorio recupera el estado de sincronización cuando se llama a `refreshProfile` o `hydrateProfile`. De hecho, como se demostró en el hallazgo UI2-01, el código actual no puede recuperarse nunca.
- **Escenario de fallo:** Al simular un conflicto de revisión devuelto por Supabase, el sistema queda bloqueado indefinidamente sin que ninguna prueba automatizada falle ni alerte de la imposibilidad de reanudar la sincronización.
- **Fix mínimo:** Añadir un test unitario `syncPendingPreferences_recovers_from_server_conflict_after_refresh` que simule una respuesta CONFLICT del RPC, invoque `refreshProfile` y compruebe que la revisión local se actualiza a la del servidor y que el outbox se desbloquea.

#### [UI2-14] Falta de prueba unitaria para números decimales con coma en `SetupDraft`
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/feature/setup/SetupViewModelTest.kt:90-101
- **Qué está mal:** En `SetupViewModelTest`, el test `balance text accepts at most two decimals and never negatives` valida únicamente `"1,250.50"` (coma como separador de miles y punto como decimal). No contiene ningún caso de prueba para números ingresados con coma decimal como `"150,50"` o `"1250,50"`. Por este motivo, el bug que multiplica por 100 el saldo inicial pasó inadvertido en el CI.
- **Escenario de fallo:** Un test con `balanceText = "150,50"` falla de inmediato en el código actual al comprobar que `balanceMinorUnits` devuelve `1505000L` en lugar de `15050L`.
- **Fix mínimo:** Añadir la aserción `assertEquals(15_050L, base.copy(balanceText = "150,50").balanceMinorUnits)` en `SetupViewModelTest`.

#### [UI3-06] Las notas CSV pueden interpretarse como fórmulas
- **Severidad:** media · **Categoría:** seguridad · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/portability/MovementExport.kt:12
- **Qué está mal:** el escape protege separadores y comillas, pero deja intactos valores de texto que una hoja de cálculo interpreta como fórmula.
- **Escenario de fallo:** una nota que empieza por `=HYPERLINK(...)` se exporta como celda ejecutable; al abrir el CSV en una aplicación de hojas de cálculo compatible puede ejecutar la fórmula o presentar un enlace engañoso.
- **Fix mínimo:** neutralizar los prefijos de fórmula en los campos de texto exportados y probar prefijos `=`, `+`, `-` y `@` junto con el escape CSV.

#### [UI3-07] Pruebas instrumentadas buscan textos que ya no existen
- **Severidad:** media · **Categoría:** test-faltante · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/androidTest/java/com/kipu/app/feature/notifications/presentation/NotificationActionsTest.kt:55
- **Qué está mal:** las pruebas esperan «Marcar leídas» y etiquetas completas como «Alerta · Condición ocurrida», mientras la pantalla muestra «Marcar todas como leídas» y separa el tipo de la fecha relativa. `NotificationCenterScreenTest.kt:103` y `:148` repite expectativas desactualizadas.
- **Escenario de fallo:** al ejecutar esas pruebas sobre la pantalla actual, las búsquedas exactas no encuentran los nodos esperados y fallan antes de validar las acciones o el recordatorio.
- **Fix mínimo:** actualizar las búsquedas para comprobar el texto y la semántica actuales, manteniendo las verificaciones de comportamiento.

#### [NAV1-02] `MaskedCardReference` lanza `IllegalArgumentException` no capturada en la composición ante formatos no canónicos de tarjeta
- **Severidad:** baja (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/ui/component/MaskedCardReference.kt:30-33
- **Qué está mal:** `MaskedCardReference` ejecuta `require(Card.isValidLastFourDigits(sanitized))` directamente en el cuerpo del composable `@Composable`. Si una tarjeta posee dígitos malformados, vacíos o no numéricos (por ejemplo, importación de extracto bancario con datos incompletos, tarjeta en borrador o error de parseo OCR en recibos), Compose lanza `IllegalArgumentException` en el hilo principal durante la medición/layout, lo que produce un crash fatal de la aplicación.
- **Escenario de fallo:** Una tarjeta en base de datos local o recibida de sincronización tiene `lastFourDigits = ""` o `"123"`. Al abrir la pantalla de Dashboard (`DashboardSections.kt:182`) o el resumen de tarjetas de crédito (`CreditCardSummaryCard.kt`), Compose invoca `MaskedCardReference`. La aserción falla y la aplicación crashea de inmediato (`FATAL EXCEPTION: main`).
- **Fix mínimo:** En lugar de `require(...)`, verificar `if (!Card.isValidLastFourDigits(sanitized))` y renderizar una máscara de degradación segura (`"•••• ----"` o `"•••• $sanitized"` truncado), garantizando que un fallo de formato visual nunca detenga la Activity.
- **Verificación (Fable):** El `require` existe, pero el constructor del dominio `Card` ya exige 4 dígitos (`Card.kt:57`), así que no se alcanza con datos del modelo. Queda como riesgo latente.

#### [NAV1-04] `RetainedRootTabs` causa `IndexOutOfBoundsException` al usar el valor por defecto `pageCount = 3` con los 4 tabs de la barra inferior
- **Severidad:** baja (el worker dijo alta) · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** verificado
- **Ubicación:** app/src/main/java/com/kipu/app/navigation/RetainedRootTabs.kt:27-68
- **Qué está mal:** `RetainedRootTabs` tiene la firma `fun RetainedRootTabs(selectedTabIndex: State<Int>, pageCount: Int = 3, ...)`. Sin embargo, `RootTab.COUNT` es 4 (Dinero, Movimientos, Planificación, Análisis). Si un llamador utiliza `RetainedRootTabs(selectedTabIndex) { ... }` omitiendo el parámetro opcional `pageCount`, o si el contenido emite 4 nodos de layout (measurables), `pageAlpha` y `pageOffset` se construyen con tamaño 3. Al iterar `pages.forEachIndexed` en el índice 3, la línea 66 invoca `pageOffset[index].value.toPx()`, arrojando `IndexOutOfBoundsException: Index: 3, Size: 3`.
- **Escenario de fallo:** Cualquier componente o pantalla que instancie `RetainedRootTabs` usando su valor por defecto `pageCount = 3` para las 4 pestañas de la app o con 4 hijos sufre un crash inmediato al renderizar el cuarto hijo.
- **Fix mínimo:** Actualizar el valor por defecto a `pageCount: Int = RootTab.COUNT` y dimensionar `pageAlpha` y `pageOffset` dinámicamente según el número de measurables provistos en el layout.
- **Verificación (Fable):** Los dos llamadores (`AccountsNavigation.kt:110`, `PlanningNavigation.kt:156`) pasan `pageCount`. El valor por defecto 3 sobra, pero hoy no causa ningún crash.

#### [NAV1-14] `MoneyText` oculta el símbolo de moneda en modo privacidad cuando el parámetro `currencySymbol` es nulo pero el texto lo incluye
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/ui/component/MoneyText.kt:38-40
- **Qué está mal:** En `formatMoneyTextDisplay`, cuando `isMasked == true`, la lógica evalúa `if (currencySymbol != null) "$currencySymbol ••••••" else "••••••"`. Si un llamador invoca `MoneyText(amount = "S/ 120.00")` sin pasar explícitamente `currencySymbol = "S/"`, el texto se enmascara como `"••••••"` perdiendo por completo la indicación de moneda. En cuentas bimonetarias (PEN y USD), el usuario pierde visibilidad de qué divisa corresponde a cada monto oculto.
- **Escenario de fallo:** En pantallas que formatean montos concatenados o rangos ("S/ 50.00"), al activar el modo de ocultar saldos el texto se convierte en `"••••••"` en lugar de `"S/ ••••••"`, impidiendo al usuario saber si el monto ocultado es en Soles o Dólares.
- **Fix mínimo:** En `formatMoneyTextDisplay`, extraer el prefijo de moneda del texto (`"S/"` o `"$"` si está presente) antes de enmascarar si `currencySymbol` es nulo.

#### [NAV1-15] Función muerta `ReceiptFlowViewModel.manual()` (5 líneas no invocadas en ningún flujo)
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/navigation/ReceiptFlowViewModel.kt:185-189
- **Qué está mal:** La función `fun manual() = work { consumeImport(); val result = store.import(...) }` no es llamada desde ninguna pantalla, navegación ni test del repositorio. La creación manual de movimientos se realiza exclusivamente a través de los formularios de `MovementsNavigation` o `QuickMovementBottomSheet`.
- **Escenario de fallo:** Código muerto que debe mantenerse y compilarse sin uso real.
- **Fix mínimo:** Eliminar la función `manual()` de `ReceiptFlowViewModel.kt`.
- **Ahorro estimado:** 5 líneas

#### [NAV1-16] `PlanningColors.kt` es un archivo redundante con una función envoltorio idéntica a `rememberCalmEmeraldColors()`
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/ui/theme/PlanningColors.kt:1-10
- **Qué está mal:** `PlanningColors.kt` define `rememberPlanningColors()` que contiene únicamente `val base = rememberCalmEmeraldColors(); return base`. No añade ni modifica ningún token visual respecto a `rememberCalmEmeraldColors()`.
- **Escenario de fallo:** Indirección innecesaria que obliga a importar y mantener un archivo adicional de 10 líneas.
- **Fix mínimo:** Sustituir las llamadas a `rememberPlanningColors()` por `rememberCalmEmeraldColors()` y eliminar el archivo `PlanningColors.kt`.
- **Ahorro estimado:** 10 líneas (elimina 1 archivo)

#### [NAV1-17] Recompilación redundante de expresiones regulares en `normalizeBrandKey` durante cada renderizado de `BrandLogo`
- **Severidad:** baja · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/ui/component/BrandLogo.kt:67, 74-80
- **Qué está mal:** En `normalizeBrandKey`, las expresiones regulares `"\\p{Mn}+".toRegex()` y `"[^a-z0-9]+".toRegex()` se compilan en cada invocación. Además, `KipuBrand.from()` evalúa `normalizeBrandKey(brand.name)` dinámicamente en un loop para cada entrada del enum. En listas con decenas o cientos de transacciones o cuentas, cada item re-compila los patrones Regex en el hilo de UI.
- **Escenario de fallo:** Desplazamiento rápido por una lista larga de cuentas o movimientos en teléfonos de gama baja causa micro-pausas (jank) al compilar repetidamente patrones Regex.
- **Fix mínimo:** Declarar los `Regex` como constantes `private val` de nivel superior y precomputar el mapa o clave normalizada de cada `KipuBrand`.

#### [NAV1-18] `KipuSegmentedControl` usa `.clickable(role = Role.RadioButton)` en lugar de `.selectable()` dentro de un `.selectableGroup()`
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/ui/component/KipuFormRows.kt:324, 339-340
- **Qué está mal:** `KipuSegmentedControl` aplica `Modifier.selectableGroup()` en el contenedor `Row`, pero los elementos individuales aplican `Modifier.clickable(role = role) { onSelect(option) }` junto con `semantics { this.selected = isSelected }`. Las directrices de accesibilidad de Jetpack Compose indican que los hijos directos de un `selectableGroup` deben usar `Modifier.selectable(selected = isSelected, role = role, onClick = ...)`, garantizando que servicios como TalkBack anuncien correctamente el grupo de selección y el estado de botón radial.
- **Escenario de fallo:** Usuarios con lector de pantalla TalkBack pueden escuchar un anuncio genérico de botón en lugar de la semántica de opción de radio seleccionada dentro de un grupo de opciones mutuamente excluyentes.
- **Fix mínimo:** Reemplazar `.clickable(...)` y `.semantics { this.selected = isSelected }` por `.selectable(selected = isSelected, enabled = enabled && !isSelected, role = role, onClick = { onSelect(option) })`.

#### [NAV1-19] Divergencia y duplicación de tokens de color en `Color.kt`, `KipuUiColors.kt` y `CalmEmeraldTokens.kt`
- **Severidad:** baja · **Categoría:** fusionar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/ui/theme/Color.kt:50-52, KipuUiColors.kt:82-86, CalmEmeraldTokens.kt:53-57
- **Qué está mal:** Coexisten tres definiciones semánticas para los mismos colores financieros: `KipuIncome` / `KipuExpense` en `Color.kt`, `KipuUiColors.positive` / `debt` en `KipuUiColors.kt`, y `incomeText` / `expenseText` en `CalmEmeraldTokens.kt`. Ciertos componentes leen de uno u otro, lo que dificulta auditorías de contraste WCAG y refactorizaciones temáticas.
- **Escenario de fallo:** Cambios en el diseño visual de colores semánticos pueden actualizarse en `CalmEmeraldTokens` pero quedar desactualizados en componentes que lean directamente `KipuIncome` o `rememberKipuColors().positive`.
- **Fix mínimo:** Unificar `KipuIncome` y `KipuExpense` para que deleguen directamente en `CalmEmeraldColors`.
- **Ahorro estimado:** 8 líneas

#### [UI1-12] `ObserveNetWorthSummary` no protege contra deudas con `remainingPrincipalMinor` negativo
- **Severidad:** baja · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/home/domain/ObserveNetWorthSummary.kt:31
- **Qué está mal:** En `ObserveNetWorthSummary.kt:31`, se mapea: `Money(it.remainingPrincipalMinor, Currency.fromCode(it.currencyCode))`. Si un pago o liquidación excede el principal debido a ajustes o amortizaciones manuales, `remainingPrincipalMinor` puede ser menor a cero. A diferencia de `ObserveAnalyticsOverview.kt:62` (`coerceAtLeast(0L)`) y `UpcomingDuesViewModel.kt` (`coerceAtLeast(0)`), aquí no se acota a 0, creando importes monetarios negativos que distorsionan los pasivos o por cobrar (un pasivo negativo se suma como activo).
- **Escenario de fallo:** Deuda por pagar con saldo de -S/ 50. En el cálculo de patrimonio, `liabilities` se vuelve -50, lo que provoca que `netWorth = realAssets - (-50) = realAssets + 50`, inflando el patrimonio neto como si la deuda fuera un activo.
- **Fix mínimo:** Agregar `.coerceAtLeast(0L)` al valor de `it.remainingPrincipalMinor` al igual que en `ObserveAnalyticsOverview`.

#### [UI1-13] Filtrado duplicado idéntico entre `AnalyticsDao.observeExpenses` y `OperatingExpenseClassification`
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/analytics/data/AnalyticsRepository.kt:20 y app/src/main/java/com/kipu/app/feature/analytics/domain/OperatingExpenseClassification.kt
- **Qué está mal:** `AnalyticsDao.observeExpenses` ya filtra exactamente en SQL: `type = 'EXPENSE'`, `status IN ('ACTIVE','CONFIRMED','REVISED')`, `sync_status NOT IN ('CONFLICT','FAILED_PERMANENT')`, `operation_kind` estándar y `legacy_kind` no operativo. Inmediatamente después, `AnalyticsRepository.observe` re-ejecuta `OperatingExpenseClassification.includes` en memoria sobre todas las filas obtenidas. La clase `OperatingExpenseClassification` existe solo para este doble filtrado redundante (que además no se hace en ingresos).
- **Escenario de fallo:** Por cada fila leída de Room, se normalizan strings (`uppercase(Locale.ROOT)`) y se evalúan sets repetidos que la base de datos ya filtró.
- **Fix mínimo:** Eliminar la clase `OperatingExpenseClassification.kt` y su llamada en `AnalyticsRepository.kt`, o delegar la validación a la capa de datos como se hace en `observeIncomes`.
- **Ahorro estimado:** 38 líneas (14 en `OperatingExpenseClassification.kt` + 24 en su test).

#### [UI1-14] `AnalyticsScreen.kt` monolítico de 1090 líneas mezcla navegación, selección de fechas, modales de filtros y modo comparación
- **Severidad:** baja · **Categoría:** dividir · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsScreen.kt:1-1090
- **Qué está mal:** El archivo tiene 1,090 líneas y agrupa responsabilidades no relacionadas: pantalla principal, encabezado con menú de periodo, diálogo modal de rango de fechas de Material3 (`RangeDialog`), bottom sheet de selección de periodo (`PeriodSheet`), bottom sheet de catálogo y filtros (`FilterSheet`), y toda la UI completa del modo de comparación (`comparisonItems`, `ComparisonCard`, `LegendBar`).
- **Escenario de fallo:** Dificulta la lectura, testing y mantenimiento aislado de los filtros o selectores de fecha; cualquier cambio a los filtros obliga a recomponer y tocar el archivo principal de la pantalla.
- **Fix mínimo:** Extraer `PeriodSheet.kt` + `RangeDialog.kt` (~200 líneas), `FilterSheet.kt` (~150 líneas) y `AnalyticsComparisonSection.kt` (~150 líneas) a archivos separados en el mismo paquete.
- **Ahorro estimado:** Archivo principal se reduce de 1090 a ~450 líneas.

#### [UI1-15] `AnalyticsCalculatorTest` contiene un test redundante de `NetWorthCalculator`
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/feature/analytics/AnalyticsCalculatorTest.kt:60-64
- **Qué está mal:** El test `netWorthIncludesReceivablesAndDeductsCardDebtOnce` en `AnalyticsCalculatorTest.kt` prueba `NetWorthCalculator.calculate`. Esta prueba es idéntica a `la deuda de tarjeta se resta una vez como pasivo` en `NetWorthCalculatorTest.kt:31-39` y no pertenece al módulo de analytics.
- **Escenario de fallo:** Mantenimiento duplicado si cambian los modelos de entrada de `NetWorthInputs`.
- **Fix mínimo:** Eliminar `netWorthIncludesReceivablesAndDeductsCardDebtOnce` de `AnalyticsCalculatorTest.kt`.
- **Ahorro estimado:** 5 líneas.

#### [UI1-16] `observeMerchantOptions` invalida y refresca los filtros ante cualquier mutación en la tabla `transactions`
- **Severidad:** baja · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/analytics/data/AnalyticsDao.kt:76-83
- **Qué está mal:** La consulta `observeMerchantOptions` hace `FROM transactions t JOIN merchant_catalog_cache m`. Room registra un observador de invalidación en la tabla `transactions`. Cada vez que el usuario crea, edita o sincroniza cualquier transacción (aunque no sea de un comercio nuevo ni afecte a las opciones de filtro), Room re-ejecuta `observeMerchantOptions`, lo que dispara `observeFilterOptions` en el ViewModel y provoca la recomposición de los selectores en pantalla.
- **Escenario de fallo:** Durante una sincronización de fondo (SyncMovementsWorker) de 50 transacciones, se producen decenas de invalidaciones innecesarias de `filterOptions`.
- **Fix mínimo:** En `observeFilterOptions` aplicar `distinctUntilChanged()` en el flujo de opciones o consultar los comercios desde una tabla derivada/caché que no se invalide en cada movimiento.

#### [UI1-17] `percentChange` puede desbordar `Int` si el importe previo es 1 céntimo y el actual es alto
- **Severidad:** baja · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsFlowSections.kt:85-89
- **Qué está mal:** En `percentChange`: `BigDecimal.valueOf(current.minorUnits - previous.minorUnits).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(previous.minorUnits), 0, RoundingMode.HALF_UP).toInt()`. Si `previous` es S/ 0.01 (1 céntimo) y `current` es un monto significativo como S/ 250,000.00 (25,000,000 minor units), el cálculo da `2,499,999,900`, superando `Int.MAX_VALUE` (2,147,483,647). La conversión `.toInt()` desborda a un entero negativo.
- **Escenario de fallo:** Un usuario con un gasto de S/ 0.01 en el mes anterior y S/ 30,000 en el mes actual ve un porcentaje negativo o distorsionado en la tarjeta de KPI de gastos.
- **Fix mínimo:** Acotar el porcentaje resultante con `.coerceIn(-999_999, 999_999)` o usar `Long` antes de convertir.

#### [UI1-18] `HomeNetWorthViewModel` oculta el estado protegido biométrico (`Protected`) bajo `Loading` perpetuo
- **Severidad:** baja · **Categoría:** datos · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/home/presentation/HomeNetWorthViewModel.kt:65 y app/src/main/java/com/kipu/app/feature/home/presentation/NetWorthCard.kt:162-166
- **Qué está mal:** Cuando `sessionCoordinator.localAccess` es `LocalAccess.Protected` (la app requiere desbloqueo biométrico), `HomeNetWorthViewModel` emite `NetWorthUiState.Loading` en lugar de un estado explícito de bloqueo. En la pantalla de inicio, la tarjeta de patrimonio muestra permanentemente un esqueleto de carga con el texto de accesibilidad "Calculando patrimonio", sugiriendo que la app está trabada o calculando datos, en lugar de indicar que está bloqueada por seguridad.
- **Escenario de fallo:** Usuario con bloqueo biométrico configurado abre la app y permanece en la pantalla bloqueada: la tarjeta de patrimonio parpadea con skeleton loaders como si estuviera pendiente de respuesta de red.
- **Fix mínimo:** Agregar `data object Locked : NetWorthUiState` y mostrar un icono o etiqueta de bloqueo como en `AnalyticsScreen`.

#### [UI2-15] Reasignación e instanciación repetida de `selectTheme` en `ProfileSettingsScreen` en cada recomposición
- **Severidad:** baja · **Categoría:** rendimiento · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/settings/presentation/ProfileSettingsScreen.kt:313-324
- **Qué está mal:** La lambda `selectTheme` se declara directamente en el cuerpo del composable: `val selectTheme: (ThemeMode) -> Unit = { mode -> ... }` sin estar envuelta en `remember`. Como `ProfileSettingsScreen` se recompone ante cualquier cambio de estado (ej. al actualizar `isSaving`, `syncState` o recibir actualizaciones periódicas), se asigna una nueva instancia de lambda en cada frame, invalidando los composables hijos `ThemeOptionChip` y forzando recomposiciones innecesarias de la barra de temas.
- **Escenario de fallo:** Recomposiciones innecesarias de los 3 chips de tema cada vez que cambia cualquier campo en `SettingsUiState` (como `isSaving` o el reloj de sincronización).
- **Fix mínimo:** Envolver `selectTheme` en `remember(themeContext, reducedMotion, currentlyDark, systemDark) { { mode -> ... } }`.

#### [UI2-16] Código muerto: `PermissionSourceGateway.createSettingsIntent` no tiene consumidores en producción
- **Severidad:** baja · **Categoría:** borrar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/settings/domain/PermissionSourceGateway.kt:9 y app/src/main/java/com/kipu/app/feature/settings/data/AndroidPermissionSourceGateway.kt:51
- **Qué está mal:** La interfaz `PermissionSourceGateway` declara `fun createSettingsIntent(source: PermissionSource): Intent`. Ninguna clase del dominio, presentación o casos de uso la invoca en producción. La navegación a ajustes se realiza a través de `AndroidPermissionSettings.open(context, source)` o delegados de UI. Mantenerla en el gateway de dominio acopla innecesariamente la interfaz de dominio a `android.content.Intent`.
- **Escenario de fallo:** Contrato hinchado que obliga a implementar intents ficticios en todos los mocks y fakes de tests (`Intent()`, `error("unused")`).
- **Fix mínimo:** Eliminar `createSettingsIntent` de `PermissionSourceGateway` y de `AndroidPermissionSourceGateway`.
- **Ahorro estimado:** 6 líneas en código principal y más de 25 líneas en mocks/fakes de pruebas.

#### [UI2-17] `PlanPurchaseScreen` asume `ZoneId.systemDefault()` para cálculo de fechas de prueba y expiración
- **Severidad:** baja · **Categoría:** datos · **Confianza del worker:** media · **Estado:** probable (confianza media del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/plans/presentation/PlanPurchaseScreen.kt:220, 452-454
- **Qué está mal:** En `PlanPurchaseScreen.kt`, el primer cobro de la prueba se calcula como `LocalDate.now().plusDays(offer.trialPeriodDays.toLong())` y las fechas de vigencia usan `Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()`. Si un usuario peruano viaja al extranjero o su dispositivo se encuentra en zona horaria UTC u otra distinta a `America/Lima`, cerca de la medianoche (23:30 Lima = 04:30 UTC del día siguiente), la fecha mostrada para el cobro o vencimiento se desfasa un día respecto a la fecha contable oficial de Kipu.
- **Escenario de fallo:** Un usuario consulta su plan a las 23:45 del 11 de noviembre en un teléfono con hora UTC; la pantalla calcula la fecha base como 12 de noviembre y muestra una fecha de cobro desfasada por un día.
- **Fix mínimo:** Utilizar la zona horaria oficial `ZoneId.of("America/Lima")` para cálculos de periodicidad y vigencia de planes.

#### [UI2-18] Duplicación redundante de `AndroidPermissionSettingsTest` entre robolectric y androidTest
- **Severidad:** baja · **Categoría:** fusionar · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/test/java/com/kipu/app/feature/settings/data/AndroidPermissionSettingsTest.kt (28 líneas) vs app/src/androidTest/java/com/kipu/app/feature/settings/data/AndroidPermissionSettingsTest.kt (70 líneas)
- **Qué está mal:** Ambos archivos prueban exactamente lo mismo: que en API 24 se use `ACTION_APPLICATION_DETAILS_SETTINGS` y en API 26+ se use `ACTION_APP_NOTIFICATION_SETTINGS`. La suite de tests unitarios locales con Robolectric (`src/test`) ya cubre y valida ambos SDKs en menos de 100 ms. Mantener el duplicado idéntico en `src/androidTest` alarga los tiempos de pruebas instrumentadas sin aportar cobertura diferencial.
- **Escenario de fallo:** Duplicación de código de prueba que requiere doble mantenimiento ante cambios en los intents del sistema.
- **Fix mínimo:** Eliminar el archivo duplicado `app/src/androidTest/.../AndroidPermissionSettingsTest.kt` conservando la suite rápida de Robolectric en `src/test`.
- **Ahorro estimado:** 70 líneas de tests.

#### [UI2-19] Renderizado de tarjeta vacía en `PlanPurchaseScreen` ante estado `READY` con `effectivePremium`
- **Severidad:** baja · **Categoría:** bug · **Confianza del worker:** alta · **Estado:** probable (confianza alta del worker, no reabierto)
- **Ubicación:** app/src/main/java/com/kipu/app/feature/plans/presentation/PlanPurchaseScreen.kt:330-345, 392
- **Qué está mal:** En `PurchaseStatus(state, ...)`: `if (state.status == PlanPurchaseStatus.READY && !state.effectivePremium) return` Si `state.status == PlanPurchaseStatus.READY` pero `state.effectivePremium == true`, la función no retorna. Pasa a renderizar `Card(...)`, pero dentro del `when (state.status)` no existe rama para `PlanPurchaseStatus.READY`, cayendo en `else -> Unit`. Si además `state.lifecycle == null`, se dibuja una tarjeta gris vacía con padding en medio de la pantalla.
- **Escenario de fallo:** Un usuario premium ingresa a la pantalla de compra en estado READY sin haber cargado el lifecycle de su suscripción: se dibuja una tarjeta gris en blanco sin contenido.
- **Fix mínimo:** En la línea 330, retornar directamente si el estado es `READY`: `if (state.status == PlanPurchaseStatus.READY) return`.

## 4. Borrar, fusionar, dividir

55 hallazgos de limpieza, con un ahorro declarado de unas **4022 líneas**. Mover o dividir archivos no cuenta como ahorro. Verifiqué el código muerto más grande (ACC2-15, ACC4-09 y CAT2-07): solo lo referencian su propio archivo, sus previews o sus tests. CAT2-07 depende de una decisión, porque la pantalla de conflictos huérfana es justo la que falta para resolver los conflictos de categorías. La única dependencia que sobra es `hilt-android-testing`, junto con sus `kspTest` y `kspAndroidTest` (H2). Tabla ordenada por ahorro:

| ID | Tipo | Qué | Líneas | Dónde |
|---|---|---|---|---|
| ACC2-15 | borrar | AccountFormScreen y CardFormScreen ya no se usan | 900 | app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/AccountFormScreen.kt:59-304; CardFormScreen.kt (completo); app/src/ |
| CAT2-07 | borrar | Pantallas y ViewModel huérfanos sin registro en rutas de navegación | 734 | app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryConflictScreen.kt:52-150; app/src/main/java/com/kipu/app/f |
| ACC4-09 | borrar | Componente obsoleto y no referenciado en producción: CreditCardSummaryCard | 498 | app/src/main/java/com/kipu/app/feature/accounts/presentation/components/CreditCardSummaryCard.kt:1-498 |
| ACC2-16 | borrar | Código muerto dentro de UnifiedInstrumentFormScreen | 300 | UnifiedInstrumentFormScreen.kt:1298-1375 (`TreeBranchItem`), :1377-1461 (`BranchProductCard`), :1465-1486 (`ReferencialTeaCard`), :1530 (`pe |
| ACC4-16 | borrar | Harness obsoleto de captura de pantallas sin aserciones en androidTest | 166 | app/src/androidTest/java/com/kipu/app/feature/accounts/presentation/Issue48CopyScreenshotTest.kt:1-166 |
| BUD1-13 | fusionar | Los fakes de test difieren del repositorio real y están duplicados; un test de otra feature vive aquí | 110 | app/src/test/java/com/kipu/app/feature/budgets/domain/BudgetFakes.kt:31-90 y app/src/test/java/com/kipu/app/feature/budgets/presentation/Bud |
| DEB1-09 | borrar | Dos planificadores de apertura solo existen para sus propios tests | 109 | app/src/main/java/com/kipu/app/feature/debts/domain/usecase/OpenPayableDebt.kt:10-43; app/src/main/java/com/kipu/app/feature/debts/domain/us |
| DEB2-11 | borrar | Código muerto: constancia PDF de cierre | 90 | P/DebtDetailScreen.kt:129-151, 720-778, y los imports `android.graphics.*`, `rememberLauncherForActivityResult`, `Dispatchers`, `withContext |
| REC1-15 | borrar | Código de edición de recurrencias sin ningún llamador | 85 | feature/recurrences/data/repository/OfflineFirstRecurrenceRepository.kt:95-138 y :301-307; feature/recurrences/domain/RecurrenceRepository.k |
| CAT2-08 | borrar | Código muerto en `CategoryDesignTokens.kt` (paletas no utilizadas y bucle inalcanzable) | 70 | app/src/main/java/com/kipu/app/feature/categories/presentation/CategoryDesignTokens.kt:55-116, 169-174 |
| UI2-18 | fusionar | Duplicación redundante de `AndroidPermissionSettingsTest` entre robolectric y androidTest | 70 | app/src/test/java/com/kipu/app/feature/settings/data/AndroidPermissionSettingsTest.kt (28 líneas) vs app/src/androidTest/java/com/kipu/app/f |
| DEB2-12 | borrar | Código muerto: alertas de vencimiento de deudas y sus tests | 65 | P/DebtDueAlerts.kt:9-69 (todo excepto `debtDueDayLabel`), test/.../DebtDueAlertsTest.kt (casi completo) |
| ACC4-10 | borrar | Código muerto: AlertTogglePill en CalmEmeraldInstrumentComponents | 46 | app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt:1761-1806 |
| SEC2-16 | fusionar | Duplicación de wrappers `LoginScreen.kt` y `RegisterScreen.kt` | 45 | app/src/main/java/com/kipu/app/feature/auth/presentation/LoginScreen.kt:1-23; app/src/main/java/com/kipu/app/feature/auth/presentation/Regis |
| MOV5-15 | borrar | `CreateSubcategoryView` es una función Composable muerta sin ningún llamador | 42 | app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:1431-1472 |
| DB1-11 | fusionar | Tests duplicados y mal ubicados | 40 | app/src/androidTest/java/com/kipu/app/core/database/MovementRoomMigrationTest.kt:57-98 vs DebtRoomMigrationTest.kt:19-46; app/src/test/java/ |
| SEC1-10 | borrar | Permanecen dos tests de plantilla sin comportamiento de Kipu | 40 | app/src/test/java/com/kipu/app/ExampleUnitTest.kt:1-16; app/src/androidTest/java/com/kipu/app/ExampleInstrumentedTest.kt:1-24 |
| UI1-13 | borrar | Filtrado duplicado idéntico entre `AnalyticsDao.observeExpenses` y `OperatingExpenseClassification` | 38 | app/src/main/java/com/kipu/app/feature/analytics/data/AnalyticsRepository.kt:20 y app/src/main/java/com/kipu/app/feature/analytics/domain/Op |
| MOV2-11 | fusionar | El mapeo de `TransactionItem` está copiado dos veces en el repositorio | 35 | app/src/main/java/com/kipu/app/feature/movements/data/OfflineFirstMovementRepository.kt:53-99 y :289-327 |
| REC1-16 | borrar | Rama de "deuda no disponible" inalcanzable desde el caso de uso | 35 | feature/calendar/domain/CalendarEventProjector.kt:98-118 |
| UI2-11 | borrar | Control fantasma en `ProfileSettingsScreen`: "Alertas de vencimiento" es texto estático sin modelo subyacente | 34 | app/src/main/java/com/kipu/app/feature/settings/presentation/ProfileSettingsScreen.kt:439-472 |
| MOV5-16 | borrar | `PastelCategoryPickerFlow` es una función alias sin llamadores | 31 | app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:657-687 |
| CAP2-14 | borrar | Código muerto en recibos y statements | 30 | app/src/main/java/com/kipu/app/feature/receipts/presentation/ReceiptReviewScreen.kt:422-437 (`ReceiptShareConsentDialog`, sin usos) y 93,98  |
| MOV3-15 | borrar | Código muerto en el área de formularios | 30 | QuickMovementViewModel.kt:733-745 (`createSubcategory`, sin llamadores), 179 y QuickMovementScreen.kt:198 (`ShowMessage` nunca se emite), Mo |
| CAP2-13 | fusionar | Los dos parsers BCP duplican utilidades y ya divergen en la validación de fechas | 25 | app/src/main/java/com/kipu/app/feature/statements/domain/BcpSavingsStatementParser.kt:29,52-56,98-100 vs BcpVisaStatementParser.kt:185-193,2 |
| MOV2-14 | borrar | Imports sin uso y helpers de propietario duplicados en el worker | 25 | app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:17-34 y :107-118; MovementHydration.kt:44-52 |
| MOV5-18 | fusionar | Duplicación de lógica: `accountTypeLabel` y `networkLabel` duplicados en múltiples módulos | 25 | app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheet.kt:881-895 |
| SEC2-15 | borrar | Constantes y funciones de extensión huérfanas en `AuthCelebrationMotion.kt` y `AccountCreatedScreen.kt` | 25 | app/src/main/java/com/kipu/app/feature/auth/presentation/AuthCelebrationMotion.kt:44,77; app/src/main/java/com/kipu/app/feature/auth/present |
| BUD1-14 | borrar | Código muerto | 22 | app/src/main/java/com/kipu/app/feature/budgets/data/local/BudgetDao.kt:50-51 (`updatePeriod`), :62-63 (`tombstonedBudgetIds`), :101-102 (`co |
| MOV2-15 | borrar | Pasos de construcción en el módulo de Hilt que sobran | 22 | app/src/main/java/com/kipu/app/feature/movements/di/MovementsModule.kt:65-87 |
| MOV2-13 | borrar | Rama de anulación remota con `ledger_entries` vacío probablemente muerta | 21 | app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt:499-519 (y su test en SyncMovementsWorkerTest.kt:481-523) |
| MOV2-12 | fusionar | El recálculo de saldo está repetido cuatro veces y ya existe un helper | 20 | app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementHydration.kt:171-178, :240-245, :450-458, :480-487 |
| MOV5-19 | borrar | Parámetro obligatorio no nulo `onCreateCategory` fuerza a múltiples pantallas a pasar lambdas que lanzan excepciones | 20 | app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:465-471 |
| MOV2-09 | borrar | La rama de `dependsOnCommandId` es código muerto y además busca con la clave equivocada | 19 | app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:363-381 |
| DEB2-19 | borrar | Rama vacía y ramas inalcanzables | 15 | P/DebtListScreen.kt:577-578; P/DebtOpeningViewModel.kt:153; P/DebtSettlementViewModel.kt:208-210; P/DebtScheduleViewModel.kt:193; P/DebtActi |
| MOV5-17 | borrar | Row huérfano con espaciado sin elementos hermanos en `AccountSelectionRow` | 15 | app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectionRow.kt:108-124 |
| ACC2-19 | borrar | Campos de modelo sin uso | 13 | InstrumentFormModels.kt:21 (`logoAcronym`, más las 10 asignaciones), :30-31 (`CatalogProductChoice.familyId/familyLabel`) |
| BUD1-15 | fusionar | Constantes, helper y JSON duplicados entre repositorio, worker e hidratación | 12 | app/src/main/java/com/kipu/app/feature/budgets/data/OfflineFirstBudgetRepository.kt:254-256 vs data/sync/SyncBudgetCommandsWorker.kt:208-210 |
| DEB2-13 | fusionar | `hasVerifiedPremium` copiado en dos ViewModels de deudas | 12 | P/DebtListViewModel.kt:114-119, P/DebtOpeningViewModel.kt:188-193 (existen otras dos copias idénticas en Goals) |
| MOV5-14 | borrar | Parámetro `mostUsedAccountId` es código muerto sin efecto en `AccountSelectorBottomSheet.kt` | 12 | app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheet.kt:188, 422, 430 y 637 |
| ACC2-08 | fusionar | La TEA se interpreta con reglas distintas al crear la tarjeta y en la pantalla de tasas | 10 | UnifiedInstrumentFormScreen.kt:495-498 (`MoneyInputParser`, sin tope) frente a RateCatalogScreen.kt:244-247, :391, :696-702 (`toDoubleOrNull |
| CAT2-11 | fusionar | `CategoriesViewModel` re-implementa el conteo de raíces ignorando las funciones canónicas de `CategoryRules` | 10 | app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoriesViewModel.kt:158-168 |
| NAV1-16 | borrar | `PlanningColors.kt` es un archivo redundante con una función envoltorio idéntica a `rememberCalmEmeraldColors()` | 10 | app/src/main/java/com/kipu/app/ui/theme/PlanningColors.kt:1-10 |
| NAV1-19 | fusionar | Divergencia y duplicación de tokens de color en `Color.kt`, `KipuUiColors.kt` y `CalmEmeraldTokens.kt` | 8 | app/src/main/java/com/kipu/app/ui/theme/Color.kt:50-52, KipuUiColors.kt:82-86, CalmEmeraldTokens.kt:53-57 |
| MOV3-14 | fusionar | Dos definiciones distintas de "movimiento especializado" | 7 | MovementEditorViewModel.kt:220-226 vs VoidMovementDialog.kt:57-65 |
| ACC2-17 | borrar | "Recordar comercio" en el simulador solo vive en memoria | 6 | InstallmentSimulatorViewModel.kt:73, :141, :175, :197, :210 |
| UI2-16 | borrar | Código muerto: `PermissionSourceGateway.createSettingsIntent` no tiene consumidores en producción | 6 | app/src/main/java/com/kipu/app/feature/settings/domain/PermissionSourceGateway.kt:9 y app/src/main/java/com/kipu/app/feature/settings/data/A |
| NAV1-15 | borrar | Función muerta `ReceiptFlowViewModel.manual()` (5 líneas no invocadas en ningún flujo) | 5 | app/src/main/java/com/kipu/app/navigation/ReceiptFlowViewModel.kt:185-189 |
| UI1-15 | borrar | `AnalyticsCalculatorTest` contiene un test redundante de `NetWorthCalculator` | 5 | app/src/test/java/com/kipu/app/feature/analytics/AnalyticsCalculatorTest.kt:60-64 |
| BUD1-16 | borrar | `EditBudget` aplica dos veces el límite al periodo activo (y en dos transacciones distintas) | 4 | app/src/main/java/com/kipu/app/feature/budgets/domain/BudgetLifecycleUseCases.kt:93-96 vs app/src/main/java/com/kipu/app/feature/budgets/dat |
| DB1-13 | borrar | `SecureLog.d` sin llamadas | 4 | app/src/main/java/com/kipu/app/core/logging/LogRedactor.kt:41-43 |
| SEC2-17 | borrar | Import no utilizado de `androidx.compose.ui.graphics.Color` en `SignOutDialog.kt` | 1 | app/src/main/java/com/kipu/app/feature/auth/presentation/SignOutDialog.kt:10 |
| ACC2-18 | dividir | UnifiedInstrumentFormScreen mezcla dos flujos en una función de ~1100 líneas | — | UnifiedInstrumentFormScreen.kt:203-1293 |
| MOV5-12 | dividir | Acoplamiento arquitectónico invertido: modales de categorías residen en `movements` y generan dependencia circular con `categories` | — | app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt:1479-2418 |
| UI1-14 | dividir | `AnalyticsScreen.kt` monolítico de 1090 líneas mezcla navegación, selección de fechas, modales de filtros y modo comparación | — | app/src/main/java/com/kipu/app/feature/analytics/presentation/AnalyticsScreen.kt:1-1090 |

## 5. Código de riesgo sin tests

Cada porción lista el test mínimo que falta para su código de riesgo: un caso que falle si el bug vuelve, no cobertura. Se repite aquí tal como lo escribió cada worker. Los tests de los bugs que se arreglen van dentro de su lote del plan (§7).

Además hay **tests rotos o vacíos**, que dan una falsa sensación de cobertura:
- MOV5-02 y SEC2-01: buscan textos que ya no existen en la pantalla.
- REC1-12: comprueba un texto que la pantalla ya no muestra.
- DEB1-08: dice probar la revocación de un permiso, pero no revoca nada.
- SQL3-10 a SQL3-13: el harness de migración puede imprimir PASS con aserciones fallidas, y los de concurrencia ya no corren con el esquema final.
- CAP2-12: tres tests que siempre se saltan sin los PDFs privados.

**MOV1**

- MovementLocalDataSource.commitRevisionAtomic/commitVoidAtomic → compra confirmada con reembolso vinculado seguida por reducción o anulación; verificar que no queda un abono huérfano (MOV1-01).
- RegisterRefundTransaction + elegibilidad local → gasto creado por el registro real y aceptado, todavía ACTIVE/SYNCED, admite devolución (MOV1-02).
- MovementLocalDataSource.redoProposal → propuesta con snapshot, no solamente JSON del comando (MOV1-03).
- MovementRejectionResolver + lectura local → rechazo compensado no marca fallido el movimiento oficial ni una revisión posterior aceptada (MOV1-04).
- QueryMovementHistory → filtro de moneda como único criterio avanzado (MOV1-06).
- MovementRevisionPlanner → transferencia con categoría GENERAL (MOV1-08).
- MovementDao.findSimilarTransactions → movimiento vigente REVISED produce advertencia (MOV1-09).

Los tests monetarios revisados sí verifican asientos exactos, rollback de escritura, reinicio Room, remanente de reembolsos, paginación y leases vencidos. No se infiere que hayan pasado: no se ejecutaron.

**MOV2**

- `MovementHydration.applyPulledTransaction` con un reembolso (con tarjeta y sin ella) que ya existe localmente → pull de un UPSERT REFUND con la forma del RPC y con la de la proyección; esperar `Success` y que el checkpoint avance (MOV2-01).
- `SyncMovementsWorker.processRevisionCommand` con el alta del mismo movimiento en backoff → la edición no debe enviarse antes que el alta (MOV2-02).
- `MovementHydration.applyPulledTransaction`, rama de outbox pendiente (399-425) → crear una propuesta desde el pull y luego `discardProposal` y `redoProposal`; hoy no hay ningún test de esa rama (MOV2-03 y MOV2-04).
- `SyncMovementsWorker.doWork` con 11 comandos listos → todos enviados en una ejecución (MOV2-05).
- `processRegisterCommand` con una cuenta afiliada en `FAILED_PERMANENT` → el comando termina rechazado (MOV2-07).
- `PlansMovementEntitlementProvider.getEffectiveEntitlement` → no tiene test unitario: un caso PREMIUM con `entitlementExpiresAt == null` y evaluación `RevalidationRequired` debería dar `verified=false`, `revalidationRequired=true`.

Sobre los tests de mi alcance: cubren bien el aislamiento por propietario, el rollback de página, el eco de ledger y el rechazo con compensación. Dos son débiles: los asserts `javaClass.simpleName.contains("Success")` de SyncMovementsWorkerTest (sería mejor comparar con `Result.success()`), y `remoteVoidOfPulledExpense…`, que prueba un formato de feed probablemente obsoleto (MOV2-13). No hay tests duplicados que convenga borrar.

**MOV3**

- `QuickMovementViewModel.saveApplication` (QuickMovementViewModel.kt:1038): deuda PAYABLE de S/ 100 y principal S/ 150 → no se llama a `settleDebt`.
- `QuickMovementViewModel` pago de tarjeta (979-1010): `payCreditCard` falla con "Insufficient funds..." → mensaje en español; origen CASH → `accountError`.
- `MovementEditorViewModel.onSave` (347-438): monto 0 → `amountError`; `Conflict` conserva mensaje; head con `cardId` → no llama a `revise`.
- Selector de fecha del editor (MovementEditorSheet.kt:771): un test Compose con `occurredAt` 20:00 Lima que pulse "Aceptar" sin cambios y verifique que `onDateChanged` no se dispara (o devuelve el mismo instante).
- `VoidMovementDialog` y `MovementVoidBalancePreview`: ningún test (ni unit ni androidTest) cubre el saldo proyectado de gasto, ingreso y transferencia (3 asserts bastan).
- `MovementEditorViewModel.onRedoConflict/onDiscardConflict`: sin test de resultado no exitoso.

**MOV4**

- `MovementHistoryViewModel.loadFirstPage` → cargar dos páginas, emitir una invalidación y comprobar que los movimientos consultados siguen accesibles sin reiniciar la navegación.
- `MovementFiltersSheetContent` → con resultados fuera de la primera página y un movimiento cerca de medianoche de Lima, verificar que el contador coincide con la consulta aplicada.
- `MovementFilterDraft.validate` y la hoja → activar comparación, aplicar y comprobar el resultado visible o la ausencia del control si se retira.

- DeleteMovementDialog → con importes ocultos, comprobar que el valor real no aparece en pantalla ni en semánticas.
- MovementHistoryRoute → dejar un solo elemento visible en una página con elementos ocultos y verificar que se carga la siguiente.
- MovementHistoryRoute → filtrar fuera una operación pendiente y verificar que sigue apareciendo el aviso de reconexión.
- MovementFiltersSheetContent → elegir USD y comprobar que el resumen no muestra soles.

**MOV5**

- `AccountSelectorDefaults.allowsCreditCards` y `title/subtitle` con `AccountSelectorPurpose.CARD_PAYMENT`: verificar que el selector para pagar tarjeta rechace tarjetas de crédito como fuente y muestre "Elige la cuenta de origen del pago de tarjeta".
- `PastelCategoryPickerBottomSheet` en `directSelectionMode = true` al pulsar una categoría raíz con subcategorías: verificar que no cierre la hoja de golpe y permita expandir el acordeón.
- `CustomPastelColorDialog` en modo oscuro con `KipuTheme(darkTheme = true)`: comprobar que seleccionar un chip pastel recomendado preserve su valor hexadecimal canónico (ej. `#DCFCE7`) y dibuje el check de selección.
- `AccountSelectorBottomSheet` con `debitCards` y `purpose = INCOME_DESTINATION`: verificar que las tarjetas de débito no se listen en la categoría Todas si las tarjetas están deshabilitadas.
- `PastelCategoryPickerBottomSheet` con emisión asíncrona de categorías: comprobar que el acordeón de la categoría actual se expanda cuando la lista se puebla con retraso.

**ACC1**

- CreditEntities.kt / CREDIT_INSTALLMENT_BALANCES_SQL → cuota con interés no nulo y pago que cubre solo capital (ACC1-01). Los fixtures Room revisados usan interés cero; el test de resumen y el asignador reciben saldos ya calculados y no cubren esta consulta.
- AccountUseCases.kt / ReactivateInstrument → reactivar débito con banco activo cuando ya existen cuatro grupos (ACC1-02). Los tests actuales pasan un booleano manual sin reproducir el contrato del débito.
- FinancialRejectionCompensationTest.kt → rechazo permanente de compra y pago alcanzando realmente el worker autenticado y el stub de API (ACC1-03).
- MovementClassificationIntegrityTest.kt → persistir y releer movimiento y asientos tras reclasificar y quitar comercio (ACC1-04).

**ACC5**

- `updateAccountAppearance` / `updateCreditCardTerms` → dos ediciones offline consecutivas, confirmación del predecesor y revisión correcta del sucesor (ACC5-02).
- `doWork` → rechazo permanente seguido de un comando válido sobre el mismo instrumento, con InstrumentSyncDao real (ACC5-03). Los mocks relajados del test instrumentado no comprueban esta dependencia.
- `processPersonalTeaCommand` / `processCreditCardTermsCommand` → 401 recuperable seguido de aceptación (ACC5-04).
- `rejectCreditCommand` → comprobar la proyección financial_movements además de transacciones, cuotas y ledger (ACC5-06).
- `InstrumentSyncScheduler` → nueva escritura durante la finalización del worker, sin depender de otra entrada a la app (ACC5-08).
- `DashboardEntryPreloader.awaitInitialData` → integración con WorkManager que pruebe la selección KEEP/REPLACE y la espera de la ejecución recién encolada. Sus tests prueban principalmente el Flow auxiliar con WorkInfo simulados; no demuestran ese protocolo real.

**ACC2**

- `UnifiedInstrumentFormScreen.handleSaveCreditCard`: test Compose del paso 3 con alias "1234" → error visible y `registerCreditCard` no llamado. Otro caso: corte = pago → error.
- `resolveRateCatalogContext` con emisores retail: test de unidad `issuer = "Tarjeta CMR Falabella"` + producto `BANCO_FALABELLA` → `Resolved`.
- `CreditCardTermsSheet`: test Compose con alias "4557880011223344" → "Guardar cambios" deshabilitado.
- `MovementClassificationEditorViewModel`: test de unidad que emita `Available` dos veces y verifique una sola suscripción a `observeCategories`. Otro que emita una clasificación nueva tras `selectCategory` y verifique que no pisa la selección.
- `RateCatalogScreen` sin TEA guardada: test Compose que espere el campo vacío (no "42.00").

**ACC3**

- `CreditCardPeriodPanel` → resumen ausente mientras la tarjeta tiene deuda: no mostrar cifras inventadas ni habilitar decisiones basadas en cero.
- `CreditCardMovementsSection` → movimientos antes y después del corte, incluido febrero.
- `CreditCardPeriodPanel` → modo privado con utilización distinta de cero, incluido el árbol de accesibilidad.
- `DashboardEmptyStatePolicyTest.archivedAccountsAndCardsDoNotGetMistakenForFirstUse` → datos de dashboard vacíos válidos con solo un instrumento archivado.

- CreditCardMovementRow → compra financiada a 12 cuotas no muestra «1 cuota».
- CreditCardActionsSection → tarjeta archivada no promete eliminar cuando solo archiva.
- AccountDetailScreen → importe de compra oculto visualmente y en accesibilidad con privacidad activa.

**ACC4**

- `CreditPurchaseDraft.kt:57-62` (`validateCreditPurchaseDraft`) → parser de TEA con decimales excesivos, exponentes o comas, verificando que devuelva error de validación sin propagar excepción aritmética no controlada (ACC4-18).
- `AccountsViewModel.kt:583-604` (`registerDebitForEditedAccount`) → registro de débito vinculado para cuentas BanBif, Scotiabank Mastercard y bancos no catalogados (ACC4-19).
- `CalmEmeraldInstrumentComponents.kt:1417-1531` (`CalmEmeraldCreditProductTree`) → colapso interactivo de grupos con producto seleccionado y entidades mono-red (ACC4-01).
- `InstrumentCardPreview.kt:107-136` → interacción de arrastre en contenedor con scroll vertical asegurando que no consuma el desplazamiento de la pantalla padre (ACC4-04).
- `CardInstallmentsSection.kt:215-289` (`CardInstallmentRow`) → visualización y legibilidad de cuotas con interés no nulo e importes de dos dígitos bajo escala de fuentes de accesibilidad.

**SQL1**

- `20260921000000_ep_cta_baseline.sql:524`, `record_opening_adjustment_v1` → apertura 10000, corrección 20000 y corrección 30000: exigir saldo final 30000 en movimientos y ledger. `financial_accounts.test.sql:8` solo comprueba que la función exista.
- La misma RPC → perder la respuesta y reenviar exactamente el comando; exigir el resultado original y un único par de hechos. Añadir además una comprobación de que un cursor anterior obtiene la corrección confirmada.
- `20260923010000_fix_category_conflict_resolution.sql:1` → elegir `LOCAL` y comprobar que cambia el nombre/estado real; el test actual solo comprueba la etiqueta `RESOLVED`. Repetir esa resolución con la misma operación también debe tener éxito: actualmente el filtro `status='OPEN'` produce `P0002` después de una respuesta perdida.
- `20260922000000_ep_cco_baseline.sql:230`, `update_category_presentation_v1` → dos sesiones editan revisión 1 y se conserva la edición perdedora como conflicto; repetir una misma operación aplicada no debe crear un conflicto ficticio.
- `20260920170000_create_auth_rate_buckets.sql:19` y `20260915000000_create_plan_selection.sql:59` → los casos concurrentes concretos de SQL1-07 y SQL1-08. Sus tests actuales cubren consumo/reintentos secuenciales.
- `movements_idempotency_stress_test.sql:25–42` ejecuta los 100 reintentos dentro de una misma sesión/transacción. Es útil para replay secuencial, pero no demuestra que dos dispositivos compitiendo por la misma operación reciban una respuesta idempotente; el test mínimo faltante usa dos conexiones y comprueba un solo efecto y respuestas conciliables. No atribuyo a SQL1 un defecto en la implementación final de registro de movimientos, que fue envuelta/redefinida en SQL2/SQL3/SQL4.
- `profile_preferences_test.sql:4–22` solo comprueba estructura y privilegios. Falta al menos un caso que llame `ensure_profile`, actualice una preferencia, repita la operación y verifique el valor y la revisión; la implementación final está en `20261103172646_profile_preferences_private_usage.sql` (SQL4), por lo que no se auditó aquí como función propia.

**SQL2**

- `delete_unused_card_v1` → primera compra concurrente con borrado, conservando tarjeta, vínculo y cuotas cuando la compra se confirma primero (SQL2-01).
- `movement_instrument_locked_v1` → edición entre cuentas con Premium y cinco bancos, y con Free dentro de cuatro grupos compartidos (SQL2-02).
- `apply_plan_quota_selection` → pérdida de respuesta seguida de archivo del recurso y reintento de la operación aceptada (SQL2-03). Los tests de selección consultados como referencia prueban reenvíos mientras los recursos siguen activos.
- Claim/complete/release de acknowledgement → recuperación del plazo y liberación tardía por el trabajador anterior (SQL2-04).
- Backfill S3 → compra legacy real sin categoría seguida de las migraciones de validación (SQL2-05); el test asignado cubre la importación de pagos, no la actualización completa de compras antiguas.
- `get_feature_access` → invocar el RPC como un usuario de prueba y comprobar que `offline_valid_until` es JSON null. `supabase/tests/database/entitlement_lease_test.sql:25–36` inspecciona el texto de la función; no verifica la respuesta y podría seguir pasando si el literal esperado quedara en una rama no ejecutada.

**SQL3**

- `internal.goal_confirmed_balance` → editar la nota de un gasto vigente y comprobar que su importe sigue descontándose del respaldo (SQL3-01).
- `reassign_goal_reserve_v1` → origen completado y dos destinos/otras metas activas de usuario Free; no producir una tercera activa (SQL3-02).
- `create_recurrence_v1` y upserts de comercios → reintento idéntico después de inactivar una referencia ya utilizada (SQL3-03/06).
- `void_debt_settlement_v1` → dos sesiones seleccionando miembros distintos del mismo pago (SQL3-04).
- `settle_debt_v1` → cuota cancelada de un cronograma reemplazado, sin residuos financieros al rechazar (SQL3-05).
- `_rec_template_references_valid` / `update_recurrence_v1` → categoría de ingresos en gasto y cambio parcial de intervalo CUSTOM (SQL3-07/08).
- RPC de recurrencias → valores integrales decimales en campos realmente convertidos a enteros, además del helper puro (SQL3-09).
- Registro de compra con sobreutilización → principal íntegro y reintento sin duplicados mediante el RPC público (SQL3-13).
- Billing RTDN → los tests asignados cubren redelivery y leases de forma secuencial; falta una prueba de dos conexiones para adquisición simultánea del mismo token y expiración de lease durante persistencia. No lo contabilicé como defecto de producción demostrado.

**SQL4**

- `internal.apply_movement_revision_v1` + `refund_transaction_v1` → compra estándar reembolsada, seguida de anulación/edición desde otro dispositivo; véase SQL4-01. `movements_refund_test.sql` prueba reintentos del reembolso, pero no su interacción con mantenimiento de la compra.
- `allocate_credit_payment_v1_pre_liability` → fondos después de un gasto revisado y de un ajuste de apertura; véase SQL4-02.
- `set_instrument_archived_v1` → reactivación Premium, grupo banco/débito compartido y reenvío atrasado tras reactivación; SQL4-03/04. `credit_card_principal_archive.test.sql:36–43` verifica el pago y PAID, pero no vuelve a intentar archivar después de liquidar.
- `internal.set_dev_plan_mode` → activar/desactivar sin usuario centinela de Auth; SQL4-05.
- `get_budget_status` → cambio de calendario entre periodos; SQL4-06. Los tests de snapshots cambian alcance manteniendo el mismo calendario mensual.
- `update_profile_preferences` → dos conexiones con el mismo operation_id; SQL4-08. La prueba actual solo repite secuencialmente.
- `account_deletion_concurrency.test.sql:5–14` → una conexión escribe mientras otra purga. El archivo comprueba modos de bloqueo y dos solicitudes secuenciales dentro de una sola transacción; no prueba la carrera que anuncia su nombre.

**DEB1**

- `DebtApi.execute` / `SyncDebtChangesWorker`: cambio de propietario entre dos comandos → nunca enviar el segundo comando con la sesión de otra persona (DEB1-01).
- `DebtLocalDataSource.closeDebt` + `DebtChangeFeedApplier.applyPage`: condonación local y eco de servidor con otra fecha → un evento, cursor avanzado y saldo único (DEB1-02).
- `DebtApi` + DTO de respuesta: HTTP 200 con `error` objeto → conflicto de dominio y no fallo de red (DEB1-03). `DebtApiTest` prueba apertura exitosa, sesión ausente y comando desconocido; no prueba esa respuesta.
- `DebtChangeFeedApplier`: conflicto de X seguido por datos independientes de Y → Y no queda bloqueada (DEB1-04). El test actual verifica expresamente que el conflicto no avance el checkpoint, pero no prueba la convergencia de las demás deudas.
- `DebtReminderWorker`: entrega + reconciliación idéntica → no publicar otra vez (DEB1-05); publicación que lanza `SecurityException` → conservar éxito y aviso local (DEB1-08).
- `SetDebtSchedule.plan`: menos centavos que cuotas → rechazo antes de construir un plan inválido (DEB1-07).

Los tests de dinero usan resultados concretos y el de precisión compara contra un cálculo decimal de mayor precisión; no los conté como tests vacíos. `DebtSettlementSyncTest` verifica persistencia e idempotencia local y anulaciones, pero no ejecuta el worker de sincronización ni una secuencia RPC/pull real; su nombre no demuestra cobertura de los contratos anteriores.

**DEB2**

- `DebtScheduleViewModel.closeDebt` (ADJUST/FORGIVE, signo y monto): test con "-10" y "10" que compruebe `principalDeltaMinor` de `DebtClosureCommand`.
- `DebtScheduleViewModel.cancelSchedule`: test de que una excepción del repositorio deja `isSaving = false` y un mensaje, sin propagar.
- `DebtSettlementViewModel.save`: principal > restante no llama al repositorio; interés > 0 en cobro envía categoría válida (relacionado con DEB2-06).
- `DebtOpeningViewModel.save`: cuenta de moneda distinta no guarda; `Duplicate` emite `Saved`.
- `DebtEditViewModel`: segunda emisión del flow no pisa el texto editado.
- `DebtDetailViewModel.delete`: rechazo `HISTORY_PRESERVED` muestra el mensaje y no navega.
- `UpcomingDuesViewModel`: flujo de error y `retry()` (DEB2-16).
- `summarizeClosure` está probado; falta un caso de ADJUSTMENT positivo (aumento) para ver el signo de `adjustedMinor`.

**SEC1**

- LocalLockCoordinator + SessionCoordinatorImpl → bloquear findSettings durante restauración y verificar que no se publica acceso privado; hoy LocalLockTest usa un coordinador cuyos métodos no hacen nada.
- AndroidXBiometricGateway → capacidad y prompt en API 29, además del único caso API 34 existente.
- SupabaseAuthRepository.signOut → fallo al marcar pendientes y retirada local garantizada de la sesión.
- AuthDeepLinkHandler + RecoverySessionInstaller → fallo transitorio y reintento del mismo callback sin reiniciar el proceso. RecoveryAcceptanceTest, leído como contrato adicional de SEC2, prueba éxito y rechazo del proveedor, pero no este caso.
- PasswordValidator + reset + login de auth-access → una contraseña Unicode atraviesa la misma política en los tres pasos. El test adicional de aceptación usa login directo del SDK, sin pasar por auth-access.
- KeystoreEncryptedSessionStorage → round trip real, ausencia de token en claro, corrupción y borrado; actualmente los consumidores lo mockean.
- PendingChangesRepositoryImpl → suma real de todas las fuentes y aislamiento entre dos propietarios.
- MainActivity → protección de la miniatura de recientes con el bloqueo activo; el test del overlay no comprueba la ventana ni el ciclo de la Activity.

**SEC2**

- `SessionEntryCurtainHost` (SessionEntryCurtain.kt:126-131): no hay test que simule la destrucción/desmontaje del composable mientras `errorExiting == true`, para verificar si `curtain.finishErrorExit()` queda bloqueado sin ejecutarse.
- `SessionEntryCurtainHost` con rutas parametrizadas (SessionEntryCurtain.kt:112-119): no hay test que verifique el levantamiento de la cortina cuando `destinationRoute` contiene argumentos dinámicos (p. ej. `debts/detail/{debtId}`) y difiere del patrón de ruta.
- `RecoveryViewModel` frente a saltos de reloj en cooldown (RecoveryViewModel.kt:47-54): no hay test que verifique el comportamiento si la hora del sistema cambia o si la app se minimiza durante 30 segundos mientras corre la corrutina en memoria.
- `OnboardingViewModel.savePage` ante fallos de persistencia (OnboardingViewModel.kt:21-27): no hay test unitario que verifique que `_error` se actualiza cuando `preferences.savePage` lanza una `IOException`.
- `OnboardingViewModel.complete` ante fallos de persistencia (OnboardingViewModel.kt:29-40): no hay test unitario que verifique que `_saving` vuelve a `false` y `_error` se puebla cuando `preferences.complete` lanza una `IOException`.

**BIL1**

- `verify-purchase/google-play-api.ts:241` → probar la respuesta JSON de `productsv2` con `purchaseStateContext`, sin sustituir el adaptador por un `ProviderPurchase` prefabricado (BIL1-02). Los tests actuales solo ejercitan los mapeadores de strings y el handler con proveedor simulado.
- `BillingRepository.purchaseResults` / `PlayBillingGateway` → aprobar un pago pendiente sin observador de pantalla y comprobar que se verifica y reconoce una vez (BIL1-03). `PlayBillingGatewayTest` prueba modelos, no llama al gateway.
- Primera vinculación del token → compra iniciada por A y recuperada por B antes de cualquier persistencia; comprobar rechazo y ausencia de escritura/reconocimiento (BIL1-01). El test de conflicto actual ya presupone un propietario registrado.
- `verifyCurrentPurchaseForOwner` → `CANCELLED` + `CANCELED_ACTIVE` vigente devuelve grant con vencimiento pagado (BIL1-05).
- `plans.validQuotaSelection` → selección de cinco raíces de gasto más cinco de ingreso alcanza la RPC (BIL1-04).
- `SyncPlanSelectionWorker` / `PlanPreferencesDao.reconcile` → recuperación tras autenticar y respuesta de revisión antigua con otra local pendiente (BIL1-07/08).

**CAT1**

- CategorySyncScheduler y SyncCategoryCommandsWorker → insertar un comando después de leer la cola y antes de terminar; comprobar que otra ejecución lo procesa.
- SyncCategoryCommandsWorker → crear, editar y borrar la misma regla offline y comprobar que ningún acuse anterior marque la última edición como sincronizada.
- merchant-search/index.ts → buscar un candidato cuyo dominio existe más allá del límite de una página REST.
- CategoryHydration y resolución de conflictos → simular dos dispositivos, recuperar el ID remoto y verificar que la resolución se confirma antes de ocultarlo localmente.

- MerchantCategoryPreferenceViewModel → seleccionar un comercio con preferencia, verificar que se puede eliminar y conservar una edición durante una emisión del catálogo.
- MerchantLogoSync → publicar dos ETags distintos con el mismo slug y comprobar que la segunda imagen usa otro objeto.
- CategoryHydration → personalizar dos veces una categoría de sistema sin presentación remota inicial y comprobar que ambas ediciones se aceptan.

**CAT2**

- `MerchantPickerViewModel.kt` / `selectMerchant` y `setProvisionalText` → Comprobar la exclusividad estricta entre comercio del catálogo y texto provisional (CAT2-13).
- `CategoriesViewModel.kt` / `submitCreateCategory` → Comprobar la creación de raíz y subcategoría con parámetros correctos y bloqueo de envíos concurrentes (CAT2-14).
- `CategoriesViewModel.kt` / `confirmDeleteCategory` → Comprobar que la confirmación de borrado invoca `setCategoryActive(id, false)` y maneja fallos (CAT2-14).
- `CategoriesScreen.kt` / `CategoryRootCard` → Comprobar que subcategorías inactivas bajo raíces activas son visualmente identificables y pueden ser reactivadas (CAT2-01).

---

**GOA1**

- GoalsApi.execute / SyncGoalsWorker → cambiar de A a B entre reclamar un CREATE_GOAL y obtener la sesión; no enviar datos con B ni confirmar el comando de A.
- GoalOutboxDao.getReady → reasignación en RETRYABLE seguida de edición de destino; impedir que la segunda adelante a la primera.
- GoalDetailViewModel.delete → dos cuentas, meta completada y reemplazo Free; finalizar todo o no alterar reservas ni otras metas. Las pruebas actuales cubren solo una cuenta o mocks que devuelven éxito.
- GoalLocalDataSource.finishRemoteCommand → conflicto de reasignación debe afectar ambos destinos; rechazo de traslado debe permitir recuperar el saldo efectivo sin borrar la evidencia.
- GoalLocalDataSource.finishRemoteCommand → recibo compuesto anterior a una edición posterior no debe revertir el estado ni marcarla SYNCED.
- OfflineFirstGoalRepository.reassignGoalReserve → fuente completada y dos metas activas Free; no producir tres activas.
- GoalContributionSheet / GoalDetailScreen → completada PEN y dos activas USD deben ofrecer reemplazo.
- GoalFormScreen / selectores de fondos → ocultación activa debe suprimir saldos de texto y semántica.
- GoalReplacementRoomIntegrationTest → corregir los IDs del caso de status inválido para probar realmente el rollback descrito en GOA1-14.

La cobertura existente sí verifica signos, importes, monedas, agregación por cuenta, reservas concurrentes, rollback local al fallar outbox, reparación de revisiones legacy y algunos reemplazos atómicos. No confundí esas pruebas de persistencia local con pruebas de rechazo remoto o aislamiento del worker durante un cambio de sesión.

**BUD1**

- `RenewBudgetPeriods` con `pendingChange` sobre un presupuesto CUSTOM_RANGE que vence (BUD1-01): test con rango 1-30 nov, pendiente a Mensual, renovar el 1 dic → debe quedar ACTIVE con periodo nuevo.
- `EditBudget` con pendiente previo (BUD1-03): editar alcance, luego solo calendario → el pendiente conserva ambos.
- `SyncBudgetCommandsWorker` 409/REVISION_CONFLICT/PARENT_COMMAND_FAILED/RETRY y `BudgetHydration` con outbox bloqueado (BUD1-12).
- `EvaluateBudgetAlerts` con `notify` que lanza o se cancela tras `tryRecord` (BUD1-04): la alerta debe poder emitirse otra vez.
- `BudgetCenterAlertNotifier` (ningún test: dedupe por id, no publicar en el sistema si ya existía, owner distinto).
- `OfflineFirstBudgetRepository.delete` de un presupuesto creado offline y aún no sincronizado (borra el UPSERT pendiente y encola `DELETE_BUDGET`): no hay test.
- `BudgetTotalsCard` (BUD1-05): sin test de la suma con alcances solapados.
- `RoomBudgetCategoryLookup.idsIncludingDescendants` solo se prueba indirectamente en `InactiveCategoryBudgetConsumptionTest`; falta un caso con categoría de sistema (`user_id` null) y árbol de 2 niveles.

**REC1**

- `OfflineFirstRecurrenceRepository.applyChangeFeed` (rama regla existente): feed con rev mayor, con y sin comando pendiente (REC1-14).
- `RecurrenceScheduleCalculator.isOccurrence` con ancla 29-feb anual: candidato 28-feb de un año no bisiesto debe ser true (REC1-05).
- `GenerateExpectedPayments`: `nextDueDate` pasada con recálculo, `endDate` anterior al día, regla pausada (no debe dejar filas EXPECTED vivas, REC1-04).
- `MatchCapturedMovementToExpected` (varios caminos: 0, 1 y más de 1 coincidencia, `chooseNone`): sin test en el alcance.
- `OfflineFirstRecurrenceRepository.update`: sin ningún test de camino feliz ni de conflicto (si se conserva, REC1-15).
- `reconcileReminder`: no hay test de que reprograme tras el recibo ni de que no re-notifique (REC1-02, REC1-07).
- `RecurrenceHydration.pullChanges`: página con operación o estado desconocido (REC1-08).

**CAP1**

- `RoomCaptureCorrelation.attach` → dos avisos de la misma fuente con igual referencia y monto, pero fechas distintas, deben conservar dos candidatos.
- `CaptureCandidateDao.discard` → usar Room real y verificar que el nombre extraído ya no quede en la fila descartada; el test existente usa un almacén falso y solo comprueba `evidenceSnippet`.
- `CapturePresentation.missingFields` → tipo de débito conocido sin cuenta debe figurar como instrumento faltante.

- `CaptureReviewViewModel.onInstrumentSelected` → captura en soles y cuenta en dólares no deben producir el mismo monto en dólares sin confirmación explícita.

- PeruNotificationParser.parse → probar agrupación con espacio y signo anterior a moneda.
- KipuNotificationListenerService → fallo transitorio antes de guardar sin segundo callback.
- CaptureFollowupWorker.doWork → cambio de dueño durante un procesador suspendido y solapamiento de trabajos.

**CAP2**

- `StatementLineEditSheet` → elegir un comercio con categoría predeterminada, cancelar y verificar que comercio y categoría del borrador no cambian.
- `ImportMovementsScreen` → simular una excepción de importación y verificar que se muestra el mensaje de error.
- `ReceiptImportNoticeSheet` → pulsar «Agregar de nuevo» en `ALREADY_DISCARDED` y verificar que llama a `onRestore`.
- `RoomStatementImportStore.setCard` rama crédito → import con tarjeta sin match y un movimiento existente igual; `setCard` debe dejar la línea `possibleDuplicate=true, selected=false`.
- `BcpVisaStatementParser` filas de resumen/`CARGO` con importe negativo → caso "EXTORNO INTERES 10.00-" que no genere INTEREST positivo.
- `BcpSavingsStatementParser` página 2 sin cabecera de columnas y fila con dos importes → caso que verifique que no se pierden líneas (o que la conciliación falla).
- `YapeReceiptParser` importe "S/ 1,500" (y "1.500") → un caso con el resultado esperado.
- `ConfirmStatementImport.paymentFailure` con mensajes distintos (sin "account") → un caso por motivo.
- `RoomReceiptCandidateStore.import/edit` solo tiene androidTest (`ReceiptCandidateRoomTest`); no corre en la suite JVM.
- `PdfBoxStatementDocumentReader` con archivo grande/muchas páginas → un caso de tope.

**DB1**

- RoomMigrations.kt `ensureCreditInstallmentStatusGuards` (1457): pagar una cuota y comprobar PAID/PARTIAL, en base nueva y migrada.
- RoomMigrations.kt `MIGRATION_36_37` (1556): caso con `reference` nula en `receipt_candidates` (el único test usa referencia no nula).
- LogRedactor.kt: password/recovery_token y `SecureLog` con throwable (que conserve el stack).
- MerchantLogoUrlResolver.kt: clave `../x` y clave con espacios/acentos.
- Migraciones 5→6 y 14→15: sin test del camino que aborta por `check`, solo el camino feliz.

**UI1**

- `ObserveAnalyticsOverview.invoke` (app/src/main/java/com/kipu/app/feature/analytics/domain/ObserveAnalyticsOverview.kt:38-80) → Test mínimo que falta: `ObserveAnalyticsOverviewTest` con repositorios mock que verifique que los vencimientos por pagar de préstamos y cuotas de tarjetas se sumen a `upcoming` agrupados por moneda hasta fin de mes, y que se seleccione la meta activa con mayor avance.
- `AnalyticsDao.observeIncomes` (app/src/main/java/com/kipu/app/feature/analytics/data/AnalyticsDao.kt:103-114) → Test mínimo que falta: inserción en `AnalyticsDaoTest` de transacciones `INCOME` con `operation_kind` estándar, `REFUND`, `DEBT_DISBURSEMENT` y `OPENING`, comprobando que solo los ingresos operativos sean emitidos.
- `AnalyticsDao.observeLedgerRows` (app/src/main/java/com/kipu/app/feature/analytics/data/AnalyticsDao.kt:117-124) → Test mínimo que falta: inserción en `AnalyticsDaoTest` de filas en `ledger_entries` vinculadas y desvinculadas de `transactions`, validando el fallback de `COALESCE(t.occurred_at, le.created_at)`.
- `AnalyticsRepository.observeFilterOptions` (app/src/main/java/com/kipu/app/feature/analytics/data/AnalyticsRepository.kt:35-56) → Test mínimo que falta: test unitario con DAO simulado que valide la deduplicación por ID de comercios y la resolución correcta de URLs de logos mediante `MerchantLogoUrlResolver`.
- `AnalyticsPeriod.previous` (app/src/main/java/com/kipu/app/feature/analytics/domain/Analytics.kt:23-32) → Test mínimo que falta: prueba unitaria con un mes de 28 días (febrero) verificando que el mes anterior resulte en 31 días completos (enero completo) en lugar de truncarse a 28 días.

**UI2**

- `OfflineFirstProfilePreferencesRepository.syncPendingPreferences` (app/src/main/java/com/kipu/app/feature/settings/data/OfflineFirstProfilePreferencesRepository.kt:137-195) → Test mínimo que falta: prueba unitaria con DAO simulado que inyecte un resultado `"CONFLICT"` desde Supabase y compruebe que no se bloquea permanentemente la reconciliación del outbox tras invocar `refreshProfile`.
- `SetupDraft.balanceMinorUnits` (app/src/main/java/com/kipu/app/feature/setup/SetupDraft.kt:43-50) → Test mínimo que falta: prueba unitaria que pase `balanceText = "150,50"` y valide que retorne `15050L` (S/ 150.50) y no `1505000L`.
- `BiometricSettingsViewModel.toggleLocalUnlock` ante fallo de Activity (app/src/main/java/com/kipu/app/feature/settings/presentation/BiometricSettingsViewModel.kt:91) → Test mínimo que falta: prueba unitaria pasando `activity = null` que verifique que el estado UI no quede congelado ni en inconsistencia si no hay Activity disponible.
- `SettingsViewModel.stepMonthStart` en límites extremos (app/src/main/java/com/kipu/app/feature/settings/presentation/SettingsViewModel.kt:144-150) → Test mínimo que falta: prueba unitaria verificando que `stepMonthStart(1)` cuando `monthStart == 28` y `stepMonthStart(-1)` cuando `monthStart == 1` sean no-op y no disparen `savePreferences()`.
- `PermissionsViewModel.grantConsent` para `OTHER_APP_NOTIFICATION_CONTENT` (app/src/main/java/com/kipu/app/feature/settings/presentation/PermissionsViewModel.kt:163-198) → Test mínimo que falta: prueba unitaria donde `captureGate.currentOwnerId()` no coincida con `userId.toString()`, validando que se emita el mensaje de error y no se persista el consentimiento en el DAO.

---

**UI3**

- `SupplementaryHydration.financialFacts` → comprobar fecha efectiva y fecha del asiento tras restaurar; la prueba existente solo cuenta filas y suma saldos.
- `OfflineFirstNotificationsRepository.syncPending` y `refresh` → intercalar una mutación local con cada operación de red, como en UI3-03 y UI3-04.
- `WidgetDataProvider` y `ClimateWidget` → siete vencimientos en un día y estado protegido durante la actualización del widget.
- `MovementExport.csv` → nota que empieza por fórmula y conserva separadores y comillas correctamente.

**NAV1**

1. `ReceiptMovementEditorHost` (`app/src/main/java/com/kipu/app/navigation/ReceiptsNavigation.kt:106-121`): No existe prueba que valide el comportamiento del botón Atrás cuando `editingMovementId != null`. Test mínimo: abrir el editor sobre `ReceiptMovementEditorHost`, disparar el evento Atrás y verificar que `editingMovementId` vuelve a `null` mientras la pantalla contenedora permanece intacta en el NavController.
2. `MaskedCardReference` (`app/src/main/java/com/kipu/app/ui/component/MaskedCardReference.kt:31-33`): No existe prueba que compruebe la resistencia a datos corruptos o no numéricos. Test mínimo: renderizar `MaskedCardReference` con `""`, `"123"` y `"ABCD"` verificando que degrade sin lanzar `IllegalArgumentException`.
3. `RetainedRootTabs` (`app/src/main/java/com/kipu/app/navigation/RetainedRootTabs.kt:27`): No existe prueba unitaria con la firma por defecto `pageCount = 3` recibiendo 4 pestañas. Test mínimo: invocar `RetainedRootTabs(selectedTabIndex)` con 4 composables hijos y verificar que no se produzca `IndexOutOfBoundsException`.
4. `ReceiptFlowViewModel` (`app/src/main/java/com/kipu/app/navigation/ReceiptFlowViewModel.kt:56-88`): No existe prueba de cambio de sesión/usuario mientras el ViewModel vive. Test mínimo: emitir `LocalAccess.Available("userA")` seguido de `LocalAccess.Available("userB")` y asegurar que `hasAccess()` es `true` y los datos cargados corresponden a `userB`.
5. `ThemeTransition` (`app/src/main/java/com/kipu/app/ui/theme/ThemeTransition.kt:38-96`): No existe prueba que verifique que el bitmap de captura de pantalla sea reciclado y liberado tras la animación o al destruirse la actividad.


## 6. Deuda declarada (H1) y basura del repositorio (H2)

**H1** (`docs/qa/audit-2026-11-11/H1.md`): el repo no tiene comentarios `shortcut:`, `ponytail:`, `TODO`, `FIXME` ni `HACK`. La deuda está escondida en otro sitio:
- 2 tests con `@Ignore` en `GoalLifecycleFlowTest`, ambos marcados `BUG:`: meta completada con reserva y meta archivada.
- 14 `assumeTrue` que saltan tests sin avisar. Corresponden a la aceptación contra el backend real (deudas e historial) y a la conciliación de extractos y OCR de Yape con PDFs privados (`docs/EECC`).
- 8 `@Suppress`; uno de ellos puede esconder una acción sin efecto en `ReceiptReviewScreen`.

Lo que más pesa es que **la conciliación de extractos y la aceptación remota de deudas no corren nunca en CI**. Fix mínimo: un fixture anonimizado y versionado por parser, y un job manual, documentado, para la aceptación remota.

**H2** (`docs/qa/audit-2026-11-11/H2.md`), verificado con `git ls-files` y `git check-ignore`:
- **`.run/`: 245 archivos trackeados (113 MB)** de evidencias (logs, dumps XML, scripts), sin regla en `.gitignore`. Fix: `git rm -r --cached .run` salvo los dos `*.run.xml`, y añadir la regla.
- `verification-report.md` está trackeado en la raíz: moverlo a `docs/qa/` o sacarlo del repo.
- **`AGENTS.md` y `CLAUDE.md` están ignorados** (`.gitignore:67-68`) y nunca se han commiteado, aunque son las instrucciones del proyecto. Es una decisión tuya.
- Basura local ya ignorada, que se puede borrar: los 40 `*.log` de la raíz, `scratch/` (1.298 archivos, 153 MB), `build/`, `app/build/` (4,5 GB), `.gradle/`, los `__pycache__/` y `.tools/powerbi-report-mcp/` (de otro proyecto).
- Pendientes de decidir si se versionan o se ignoran: `skills-lock.json`, `.claude/` y `.agents/`.
- **Secretos:** no hay ninguno real trackeado. Queda pendiente una revisión de privacidad de 211 PNG en `docs/referencias/` y 61 dumps XML con importes, que nadie abrió.
- El parche de `.gitignore` propuesto está en H2 §4, sin aplicar.

## 7. Plan de cambios

Las decisiones de la columna «¿Decisión tuya?» quedaron tomadas el 2026-11-11 (ver §8). Lo que sigue pendiente son comprobaciones externas, no decisiones.

La memoria del proyecto dice que en Kipu los workers de Orca van en secuencia, sobre `main` y sin Spec Kit, así que los lotes están en orden de ejecución. Cada lote trae sus tests mínimos. «Dispositivo» quiere decir `installDebug` + `am instrument` en A16 (USB) y S24 (adb inalámbrico), nunca `connectedAndroidTest`. Lo que toque Supabase remoto (aplicar migraciones o reparar datos) necesita tu aprobación expresa en cada lote.

| Lote | Qué arregla | Hallazgos | Archivos principales | Modelo y effort | Verificación | ¿Decisión tuya? |
|---|---|---|---|---|---|---|
| **L0 Higiene del repo** | Sacar `.run/` y `verification-report.md` del índice, parche de `.gitignore`, quitar `hilt-android-testing` | H2-03, H2-08, H2-10–12, H2 §5.1 | `.gitignore`, `app/build.gradle.kts`, `gradle/libs.versions.toml` | Haiku medium o GPT 6 Luna high | `git status`; compilar `assembleDebug` y `compileDebugUnitTestKotlin` | Sí: versionar `AGENTS.md`, `CLAUDE.md`, `.claude/` y `skills-lock.json` |
| **L1 Corrección de aperturas** | Revertir el ajuste vigente, recibo idempotente y publicación en el feed; el cliente consume el nuevo evento | SQL1-01, SQL1-02, SQL1-03 (y ACC5-01) | migración nueva; `OfflineFirstFinancialInstrumentsRepository.kt`; `MovementHydration.kt` | Codex Sol high | pgTAP: 100→200→300 da 300; replay idempotente; un cursor anterior recibe la corrección. Unit test de hidratación. Dispositivo: corregir dos veces en A16 y ver el saldo en S24 | Sí: forma del evento en el feed; si hay saldos ya corruptos en remoto, script de reparación aparte |
| **L2 Reembolsos y anulación de compras con tarjeta** (decisión tomada el 2026-11-11) | (1) Quitar el reembolso de gastos normales: anular o editar el monto lo cubre; se borran su entrada en la UI, su rama en la elegibilidad y su camino en el RPC. (2) Permitir eliminar una compra con tarjeta (error o compra no concretada) mientras ninguna cuota tenga pagos aplicados: RPC nuevo que anula la compra y sus cuotas, revierte el asiento y lo publica en el feed; la app abre la opción en `canVoidMovement` y `canDeleteFromHistory` y la hidratación acepta la compra anulada. Con pagos aplicados se bloquea con el aviso «Esta compra ya tiene pagos aplicados. Si el banco te la devolvió, registra un reembolso». (3) El reembolso queda solo para compras con tarjeta reales y extornos del estado de cuenta, y cualquier reembolso (también los de gastos normales que ya existan) se puede eliminar del historial, lo que exige quitar el bloqueo de `refund_of_transaction_id` en `revise_or_void`; arreglar su hidratación y que la propuesta hidratada tenga el formato correcto | MOV2-01, SQL4-01 (y MOV1-01), MOV1-02, MOV2-04 (y MOV1-03), MOV3-13 | `MovementHydration.kt`, `RegisterRefundTransaction.kt`, `MovementLocalDataSource.kt`, `VoidMovementDialog.kt`, `MovementHistoryDelete.kt`, `MovementRefundSheet.kt`; migración nueva para anular compras con tarjeta y retirar el reembolso de gastos normales | Codex Sol high | pgTAP: anular una compra con tarjeta sin pagos (cuotas VOIDED, deuda de la tarjeta recalculada, cambio en el feed) y rechazo con pagos aplicados; reembolso de un gasto normal rechazado; eliminar un reembolso existente. Unit test: pull de un REFUND con tarjeta y de una compra con tarjeta anulada. Dispositivo: eliminar una compra en cuotas en A16 y verla desaparecer en S24 | Decidido: eliminar con un toque si no hay pagos aplicados; reembolso solo con tarjeta; cualquier reembolso se puede eliminar del historial (al quitarlo vuelve a contar la compra original) |
| **L3 Política común de outbox** | Clasificación HTTP compartida (401 reintentable); qué pasa con los sucesores de un comando FAILED o CONFLICT; `APPEND_OR_REPLACE`; sin tope de 10; payload ilegible rechazado; clave de idempotencia por borrador | ACC5-03, ACC5-04, ACC5-07, ACC5-08, MOV2-05, MOV2-07, MOV2-08, MOV2-09, BUD1-02, REC1-01, REC1-03, CAT1-02, DEB1-04, UI2-01, CAP1-06, MOV3-05, DEB2-09 | un helper en `core/session` (o `core/sync`) más los workers `Sync*Worker` y sus schedulers | Codex Sol high para el helper, cuentas y movimientos; después Codex Terra medium para presupuestos, recurrencias, categorías y deudas (dos workers en secuencia) | Por módulo, un unit test de rechazo seguido de un comando válido (el válido se envía) y de 401 seguido de APPLIED. Dispositivo: modo avión con 20 comandos y reconexión | Sí: qué ve el usuario ante un comando rechazado (descartar, reintentar o quedarse con el del servidor). Hoy cada módulo muestra algo distinto o nada |
| **L4 Revisiones y orden** | Encadenar la revisión confirmada del comando anterior; un solo comando en vuelo por agregado; rebobinar el pull tras un CONFLICT; bloqueo y deduplicación en las RPC de categorías | ACC5-02, MOV2-02, MOV2-03, CAT1-01, CAT1-03, CAT1-07, CAT2-02, GOA1-01, GOA1-03, UI3-03, UI3-04, SQL1-04, SQL1-05, SQL1-06, SQL2-01, SQL4-04 | repositorios offline-first de cuentas, movimientos, categorías, metas y notificaciones; migración de categorías | Codex Sol medium | Unit test de dos ediciones offline sobre la revisión 5 que terminan sincronizadas; pgTAP con dos sesiones; dispositivo: editar lo mismo en A16 y S24 | No (depende de L3) |
| **L5 Rechazos con efecto local** | Compensar de forma atómica una creación, compra o traslado que el servidor rechazó; borrar una tarjeta sin enviar | ACC5-05, ACC5-06, ACC5-09, GOA1-05, GOA1-02, GOA1-06 | `OfflineFirstFinancialInstrumentsRepository.kt`, `SyncInstrumentCommandsWorker.kt`, `GoalLocalDataSource.kt`, `OfflineFirstGoalRepository.kt` | Codex Sol medium | Room test de un FREE_LIMIT_REACHED tras crear una cuenta con apertura, sin saldo utilizable | Sí: qué hacer con una cuenta rechazada que ya tiene movimientos |
| **L6 Deudas** | Comprobar el dueño por comando (también en metas, GOA1-09, y en `CategoriesApi`); DTO de error como objeto; eco de la condonación; avisos repetidos; tope de importe; cuotas en cero; signo del ajuste; vista previa igual a lo guardado | DEB1-01, GOA1-09, DEB1-02, DEB1-03, DEB1-05, DEB1-06, DEB1-07, DEB2-01, DEB2-03, SQL3-04, SQL3-05 | `DebtApi.kt`, `DebtDtos.kt`, `DebtChangeFeedApplier.kt`, `DebtLocalDataSource.kt`, `DebtScheduleViewModel.kt` | Codex Sol medium | `DebtApiTest` con la respuesta CONFLICT real; test de cambio A→B entre dos comandos; dispositivo: condonación offline y sync | Sí: ¿la fecha de una condonación es la del registro offline o la del servidor? |
| **L7 Billing** | Exigir `obfuscatedAccountId` en el servidor; colector de compras que viva más que la pantalla; cupo 5+5 en `plans`; grant para una suscripción cancelada pero vigente; refresco con gracia; interruptor QA; precio real en la oferta | BIL1-01–06, SQL4-05, MOV4-05, UI2-03 | `BillingRepository.kt`, `PlayBillingGateway.kt`, `verify-purchase/*`, `plans/index.ts`, migración | Codex Sol medium | `deno test` de las funciones; unit test de `BillingRepository`; la compra real necesita la pista de pruebas internas de Play (no se puede probar con `installDebug`) | Sí: compras antiguas sin `obfuscatedAccountId`; desplegar las edge functions (remoto) |
| **L8 Privacidad y seguridad** | `FLAG_SECURE` o `setRecentsScreenshotEnabled(false)`; widgets en blanco con la app bloqueada; enmascarado vía `MoneyText`; borrar el texto de una captura al descartarla; rechazar alias con aspecto de número de tarjeta; biometría en Android 9 y 10; contraseña igual en cliente y servidor; signOut a prueba de fallos; timeout de la cortina | SEC1-01–05, UI2-04, UI2-05, UI3-02, MOV4-04, ACC3-03, DEB2-05, CAP1-02, ACC2-01, SEC2-03, SEC2-04 | `MainActivity.kt`, `WidgetRefresher.kt`, `AndroidXBiometricGateway.kt`, `PasswordValidator.kt`, `auth-access/index.ts`, `CaptureDaos.kt`, pantallas citadas | Codex Terra medium (Opus medium para las piezas visuales) | Unit test de `PasswordValidator` frente a la edge function; dispositivo: Recientes con la app bloqueada y widget bloqueado. Biometría en Android 9 y 10 solo en emulador, porque A16 y S24 son recientes | Sí: `FLAG_SECURE` también impide tus capturas de QA; widgets durante el bloqueo; contraseña ASCII o Unicode |
| **L9 Fechas y cálculos** | Fecha del editor en zona Lima; avance de recurrencias; cambio pendiente en rango personalizado; ciclo con la fecha de corte; deuda de tarjeta ausente frente a cero; comparación de meses; moneda en la captura; duplicados en el estado Visa; fondos disponibles con revisiones; cupo de reactivación; respaldo de metas al editar | MOV3-01, REC1-02, BUD1-01, ACC3-01, ACC3-02, ACC1-01, ACC1-02, UI1-01, UI1-02, UI2-02, CAP1-01, CAP1-04, CAP1-05, CAP2-01, SQL2-02, SQL3-01, SQL3-02, SQL3-03, SQL4-02, SQL4-03, SQL4-06 | varios; la parte SQL en una migración | Codex Sol medium para la parte SQL; Codex Terra medium para el cliente | Unit tests con fechas en `America/Lima` (fin de mes, febrero, día de corte); pgTAP de fondos tras una revisión | Sí: ¿la deuda de tarjeta incluye intereses? ¿La recurrencia avanza sola? ¿La fecha de corte entra en el ciclo? |
| **L10 UI y copy** | Crash por alias inválido; tasas de CMR, Ripley y Cencosud; datos SBS inventados; filtros que no hacen nada; selector de categorías; colores en tema oscuro; BanBif y red del débito; subcategorías inactivas; errores ocultos al guardar categorías | ACC2-02, ACC2-03, ACC2-04, MOV4-01, MOV5-03, MOV5-04, ACC4-01–04, CAT2-01, CAT2-03, UI3-05, NAV1-03, NAV1-05 y los medios de UI | pantallas de `presentation` citadas | Opus medium (implementación visual) | Compose tests y capturas en A16 y S24 | Sí: comparación con el mes anterior; modo directo con subcategorías; ¿BanBif se excluye a propósito? |
| **L11 Tests rotos** | Arreglar o borrar los tests que dan falsa cobertura | MOV5-02, SEC2-01, REC1-12, DEB1-08, SQL3-10–13, CAP2-12 | tests citados | GPT 6 Luna high o Haiku medium | Que fallen sin el fix y pasen con él; `am instrument` de las clases tocadas | No |
| **L12 Limpieza** | Borrar código muerto y fusionar duplicados (§4) | ACC2-15, ACC2-16, ACC4-09, DEB1-09, MOV2-09, MOV2-11, MOV2-12, MOV2-14, MOV2-15, MOV2-13 (cuando se confirme el despliegue) y el resto de §4 | varios | Codex Terra medium | Compilar más unit tests; `am instrument` de las pantallas tocadas | Sí, para CAT2-07 (pantalla de conflictos) y MOV2-13 (versión desplegada) |

**Cómo se trabaja (acordado el 2026-11-11):**
- Workers de Orca en secuencia, sobre `main`. Cada worker compila; yo reviso el diff con Opus medium; después va **un commit por lote**, sin co-autor de Claude.
- Los lotes de dinero y sync (L1, L2, L3 y L6) escriben sus tests unitarios y pgTAP en el mismo lote. Todos los tests se corren juntos al final, junto con las pruebas en dispositivo (`installDebug` + `am instrument`, Codex Sol high).
- Los cambios en Supabase remoto se aplican **cada tres lotes**, con tu OK justo antes de aplicarlos. Grupos: L0–L2 (migraciones de L1 y L2); L3, L3b y L4 (migración de L4); L5–L7 (migración de L6 y deploy de las funciones de L7); L8–L10 (`auth-access` y migraciones de L9).
- Las specs se actualizan en paralelo con Gemini 3.8 Flash high, solo en `docs/` y `specs/`.

Orden y dependencias:
- L0 va primero porque es barato y deja limpio el `git status` de los lotes siguientes.
- L1 y L2 tocan el mismo hidratador y van uno detrás del otro. L2 ya tiene su decisión de producto tomada.
- L3 va antes que L4 y L5, que se apoyan en la política común.
- L6 y L7 son independientes del resto y se pueden adelantar si te urge billing.
- L8 a L12 no bloquean nada.

En cada lote: el orquestador revisa el diff con Opus medium (o lo hace él mismo si ya tiene el contexto), las pruebas funcionales las hace Opus medium y se commitea solo cuando lo apruebes.

## 8. Decisiones de producto (aprobadas el 2026-11-11)

Las 16 preguntas que dejó la auditoría quedaron respondidas. Antes de implementar cada lote, conviene pasar su decisión a la spec de la épica correspondiente. Las marcadas con **Comprobar** necesitan un dato externo antes de su lote, no una decisión nueva.

1. **Corrección de apertura (L1).** Se publica en el feed como proyección completa de la cuenta: saldo de apertura efectivo más todos sus hechos (apertura, reversiones, ajustes), igual que ya se hace con las transacciones. **Comprobar:** una consulta de solo lectura en remoto que cuente las cuentas con más de un ajuste de apertura (necesita tu permiso). Si sale cero, no hace falta reparar datos.
2. **Reembolsos (L2).**
   - En gastos normales se quita el reembolso: anular o editar el monto lo cubre.
   - Una compra con tarjeta registrada por error se elimina con un toque mientras ninguna de sus cuotas tenga pagos aplicados. Con pagos aplicados se bloquea y se ofrece el reembolso.
   - El reembolso queda solo para compras con tarjeta reales y para los extornos de la importación.
   - Cualquier reembolso, incluidos los de gastos normales que ya existan, se puede eliminar del historial como cualquier otro movimiento, para no dejar basura. Al eliminarlo vuelve a contar el efecto de la compra original. Por eso no hace falta migrar ni conservar los reembolsos antiguos.
   - Fecha del reembolso: igual o posterior a la compra y nunca futura.
3. **Comandos rechazados (L3).** Un solo patrón en todos los módulos:
   - El elemento se marca «No sincronizado» con dos acciones: «Reintentar» (solo si el error es temporal) y «Usar la versión del servidor».
   - Las altas rechazadas se muestran con ese aviso y la acción «Eliminar», y no cuentan en saldos.
   - No habrá pantalla de fusión campo a campo.
4. **Pull compartido.** Se unifica en un solo pull que reparte por tipo, en un lote propio después de L1 a L3.
5. **Deuda de la tarjeta (L9).** Incluye el interés de las compras confirmadas con interés. El simulador sigue sin crear deuda. Hay que actualizar la spec de EP-CTA.
6. **Recurrencias (L9).** La próxima fecha se calcula desde la regla y no se guarda en `next_due_date`. La pantalla para editar recurrencias se aplaza.
7. **Ciclo de tarjeta (L9).** El día de corte pertenece al ciclo que cierra. El día de pago no puede coincidir con el de corte, ni en el alta, ni en la edición, ni en el dominio. **Comprobar:** con un estado de cuenta real de BCP.
8. **Condonaciones (L6).** La fecha del hecho es la del registro offline. El RPC acepta `occurred_at` del comando y valida que no sea futura ni anterior a la apertura de la deuda.
9. **Privacidad (L8).**
   - `FLAG_SECURE` cuando el bloqueo local esté activo, nunca en el build debug.
   - Los widgets muestran «Kipu bloqueado» mientras la app esté bloqueada.
   - El enmascarado de saldos se aplica en todas las pantallas, vía `MoneyText`.
10. **Contraseñas (L8).** Se aceptan letras Unicode. La edge function valida «al menos una letra y un número en cualquier alfabeto», con el texto normalizado a NFC en el cliente y en el servidor.
11. **Billing (L7).**
    - `obfuscatedAccountId` es obligatorio. **Comprobar** en Play Console si existen compras antiguas sin ese dato; solo si las hay, vinculación por posesión del token para compras anteriores a una fecha de corte fija.
    - La oferta muestra el precio real de Play y se quita «AHORRA 40%», salvo que se calcule con precios de Play.
12. **Categorías (L4 y L12).** Los conflictos de presentación (nombre, icono, color) se resuelven solos: gana el último en escribir. Se borra `CategoryConflictScreen`. Las reglas de comercio se sincronizan en el sync normal.
13. **UI (L10).**
    - Se quita la comparación con el mes anterior de los filtros. En análisis se mantiene y compara también ingresos.
    - En el selector de categorías en modo directo, tocar una raíz con hijos abre las subcategorías, y una fila «Usar "<raíz>"» elige la raíz.
    - BanBif se incluye en débito, eligiendo el banco por su código guardado y no por el preset.
    - El catálogo de tasas muestra «Sin dato SBS» en lugar de cifras de ejemplo.
14. **Zona horaria (L9).** `America/Lima` fija. El valor por defecto del RPC de presupuestos pasa de `UTC` a `America/Lima`. `user_profiles.time_zone` queda sin usar.
15. **Repo (L0).**
    - Se versionan `AGENTS.md` y `CLAUDE.md`.
    - `.claude/settings.json` y `skills-lock.json` se versionan solo si quieres compartir las skills con cualquier clon; si no, se ignoran.
    - Se revisan las capturas de `docs/referencias/` y las que tengan datos reales pasan a `_local-only/`.
16. **Despliegue (resuelta).** Lo comprobé con `list_migrations` y `list_edge_functions`. Las 116 migraciones del repo están aplicadas en remoto y las 9 edge functions están activas. MOV2-01 bloquea solo al dispositivo que creó el reembolso y MOV2-13 es probablemente código muerto. Que el código de cada edge function desplegada coincida con el del repo no se pudo comprobar.

## 9. Falsos positivos y duplicados descartados

UI3-01 lo descartó Fable al verificar. GOA1-04 lo descartó su propio worker en la relectura.

| ID | Estado | Hallazgo | Motivo |
|---|---|---|---|
| ACC5-01 | duplicado | La segunda corrección de apertura usa contratos distintos en móvil y servidor | Misma causa que SQL1-01 vista desde el cliente (el móvil revierte el ajuste vigente, el RPC el original). Se arregla en SQL1-01. |
| GOA1-04 | falso positivo | Descartado: no se acreditó que la organización visual deba sincronizarse | Descartado por el propio worker en la relectura GOA1-v2: no hay un requisito acreditado que lo respalde. |
| MOV1-01 | duplicado | Anular una compra reembolsada deja un abono sin compra | Misma regla que SQL4-01 en el cliente (`collectReferences` no incluye reembolsos). Arreglar juntos. |
| MOV1-03 | duplicado | Rehacer una propuesta hidratada usa otro formato JSON | Mismo defecto que MOV2-04 (propuesta hidratada con `commandId`/snapshot en otro formato). Se arregla en MOV2-04. |
| SQL2-06 | duplicado | Referencia cross-módulo: fondos disponibles omiten movimientos editados | Referencia cruzada de SQL4-02. |
| UI3-01 | falso positivo | La restauración multiplica por mil las fechas financieras | `FinancialMovementEntity.effectiveAt` se guarda en microsegundos (el DAO hace `effectiveAt / 1_000L` para obtener ms y el repositorio guarda `toEpochMilli() * 1000L`). `timestamp()` devuelve ms, así que el `* 1000L` es correcto. |

Además bajé de severidad 4 críticos y 22 altos que estaban inflados (21 de los 26 cambios son de Gemini). Cada uno lleva en §3 la severidad original y el motivo. Por ejemplo, MOV5-01 no falla con cualquier hash negativo, solo con `Int.MIN_VALUE`, y SEC2-02 es bajo contraste, no un spinner invisible.

## 10. Lo que quedó sin auditar o sin verificar

- **No se ejecutó nada**: ni builds, ni tests, ni dispositivos, ni SQL. Todos los escenarios son deducciones del código. Tampoco se conoce el estado desplegado en Supabase, porque la regla prohibía consultarlo.
- **Verificación parcial**: Fable abrió los 8 críticos y 49 de los 102 altos. Los demás altos, y todos los medios y bajos, figuran como «probable» con la confianza de su worker. Los de Codex Sol, Opus y Sonnet resultaron fiables en la muestra; los de Gemini tendían a inflar la severidad.
- **Cobertura desigual**:
  - Varios workers leyeron los `androidTest` solo por el nombre de sus casos (DEB2, BUD1, CAP2, DB1, MOV4, ACC3).
  - CAP2 dejó composables grandes sin leer línea a línea.
  - DEB2 leyó 60 de las 151 líneas de `DebtCreationTypeSheet.kt`.
  - Hubo pasadas de refuerzo con Codex: GOA1 se releyó entero (72 archivos) y CAP2 completó su presentación. CAP1 tuvo una pasada dirigida al listener y al parsing, pero no terminó de releer `CaptureReviewViewModel`.
  - Cada informe detalla lo suyo en su sección «Lo que no revisé».
- **Fuera de todas las porciones**: `app/src/main/res` salvo las reglas de backup y de red (SEC1), `scripts/`, `.specify/`, la configuración de CI y de Gradle más allá de las dependencias (H2), y las plantillas de correo más allá de lo que miró SEC1.

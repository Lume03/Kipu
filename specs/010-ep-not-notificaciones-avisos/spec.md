# Feature Specification: EP-NOT — Centro de Notificaciones y Avisos (HU-42)

**Feature Branch**: `feat/010-ep-not-hu-44-recordatorios`

**Created**: 2026-09-26

**Status**: Refined
**Refined**: 2026-10-19 — Incremento Sprint 6 HU-44: anticipación configurable, deduplicación y reprogramación de recordatorios.

**Input**: User description: "HU-42 (5 pts): centro único para consultar alertas y recordatorios, abrir el objeto relacionado con navegación resiliente, administrar lectura y borrado lógico, y mostrar el contador de avisos no leídos."

## User Scenarios & Testing

### User Story 1 — Consultar alertas y recordatorios (Priority: P1)

Como usuario de Kipu, quiero consultar mis alertas y recordatorios en un centro único para abrir el contexto que requiere atención.

**Why this priority**: El centro unificado entrega el valor principal de HU-42 y es el destino de la campana de notificaciones.

**Independent Test**: Con avisos del usuario disponibles, abrir el centro permite reconocer alertas y recordatorios, filtrar la lista y distinguir la fecha prevista de un pago realizado.

**Acceptance Scenarios**:

#### Escenario 1: Alerta

- **Dado que** ocurrió cruce de crédito,
- **Cuando** abre el centro,
- **Entonces** ve condición y tarjeta relacionada.

#### Escenario 2: Recordatorio

- **Dado que** se aproxima vencimiento,
- **Cuando** consulta aviso,
- **Entonces** ve fecha prevista distinguida de pago realizado.

### User Story 2 — Abrir el objeto relacionado con seguridad (Priority: P1)

Como usuario, quiero abrir el objeto exacto relacionado con un aviso y recibir una explicación si ya no existe, sin que la navegación falle.

**Why this priority**: El aviso solo es útil si lleva al contexto correcto y no puede provocar un fallo cuando los datos cambian.

**Independent Test**: Abrir un aviso relacionado con una entidad existente llega a su detalle; abrir uno cuyo destino no existe mantiene el centro visible y muestra un mensaje no bloqueante.

**Acceptance Scenarios**:

#### Escenario 3: Navegar

- **Dado que** selecciona un aviso,
- **Cuando** lo abre,
- **Entonces** accede al detalle exacto.

#### Escenario 4: Objeto eliminado

- **Dado que** se eliminó físicamente un presupuesto,
- **Cuando** abre aviso anterior,
- **Entonces** se informa que ya no existe y no falla la navegación.

### User Story 3 — Mantener organizado el centro (Priority: P2)

Como usuario, quiero filtrar, marcar avisos como leídos y retirar los que ya no necesito para mantener claro qué requiere atención.

**Why this priority**: Las acciones de gestión reducen ruido y hacen que el contador represente lo que todavía requiere atención.

**Independent Test**: Marcar un aviso, marcar todos y retirar uno actualiza la lista y el contador solo para la cuenta activa; un aviso retirado deja de aparecer, pero su historial remoto no se borra físicamente.

**Acceptance Scenarios**:

1. **Dado que** hay avisos de distintos tipos, **Cuando** selecciona «Alertas» o «Recordatorios», **Entonces** ve solo los elementos de esa categoría; «Todos» restaura la lista completa.
2. **Dado que** hay avisos no leídos, **Cuando** marca uno o marca todos como leídos, **Entonces** sus estados cambian y el contador refleja el total restante.
3. **Dado que** retira un aviso, **Cuando** vuelve a consultar el centro, **Entonces** el aviso deja de mostrarse sin eliminarse físicamente.
4. **Dado que** el usuario abre el centro sin avisos activos, **Cuando** se completa la carga, **Entonces** ve «Todo al día. No tienes avisos pendientes».
5. **Dado que** el usuario abre la campana de avisos financieros, **Cuando** elige la acción, **Entonces** llega a PNOT y no a la bandeja P19 de importaciones.
6. **Dado que** el dispositivo está sin conexión, **Cuando** el usuario marca o retira un aviso, **Entonces** el cambio se refleja localmente y se sincroniza al recuperar conectividad.
7. **Dado que** el usuario cambia de cuenta, **Cuando** el centro vuelve a observar avisos, **Entonces** solo aparecen los avisos de la nueva cuenta.
8. **Dado que** el usuario tiene activado movimiento reducido, **Cuando** abre el centro o retira un aviso, **Entonces** la información permanece comprensible sin animación decorativa.

## Edge Cases

- La lista está vacía, todos los avisos están leídos o no hay resultados para el filtro seleccionado.
- El usuario cambia de cuenta mientras el centro está abierto; ninguna lista, mutación ni contador de otra cuenta puede aparecer.
- El destino fue eliminado, está marcado con tombstone, usa un tipo desconocido o no tiene una pantalla registrada.
- El usuario marca avisos como leídos o los retira mientras está sin conexión; el estado local queda disponible y la operación se sincroniza al recuperar conectividad.
- Una acción se reintenta después de un timeout o una respuesta duplicada; no se crean avisos ni mutaciones duplicadas.
- La cuenta tiene más de 99 avisos no leídos; el badge permanece compacto y comunica «99+».
- El usuario abre un aviso recordatorio antes de su fecha prevista; el contenido no debe presentarlo como un pago ya realizado.

## Requirements

### Functional Requirements

- **FR-001**: El centro MUST mostrar en una lista unificada las alertas y los recordatorios del usuario autenticado, ordenados del más reciente al más antiguo.
- **FR-002**: El centro MUST distinguir visual y textualmente una condición ocurrida de un evento futuro con fecha prevista. MUST reconocer `BUDGET_ALERT`, `BILLING_DUE`, `GOAL_REACHED`, `CREDIT_THRESHOLD` y `SYSTEM`, además del tipo histórico de cruce de crédito que ya emite el servidor.
- **FR-003**: El centro MUST ofrecer los filtros «Todos», «Alertas» y «Recordatorios» y un estado vacío con el copy «Todo al día. No tienes avisos pendientes».
- **FR-004**: Al abrir un aviso, el sistema MUST resolver el objeto exacto indicado por su tipo e identificador de referencia (`card`, `budget`, `debt` o `goal`) cuando el objeto y su pantalla estén disponibles.
- **FR-005**: Si el objeto referido fue eliminado, no existe o no tiene un destino registrado, el sistema MUST conservar el centro en pantalla, mostrar un mensaje amigable y no lanzar una excepción de navegación.
- **FR-006**: El sistema MUST permitir marcar un aviso como leído y marcar como leídos todos los avisos activos de la cuenta actual.
- **FR-007**: El sistema MUST permitir retirar un aviso mediante borrado lógico; las consultas del centro MUST excluir avisos retirados y la operación MUST conservar su registro remoto.
- **FR-008**: La campana del TopAppBar MUST mostrar el total de avisos activos no leídos de la cuenta actual y actualizarse tras las acciones individuales o masivas.
- **FR-009**: La lectura, el contador y las acciones de gestión MUST estar aislados por usuario; cambiar de cuenta MUST sustituir la observación local por los avisos de la cuenta nueva.
- **FR-010**: El usuario MUST poder consultar la última copia local y ejecutar acciones de lectura o retirada sin conexión; los cambios pendientes MUST converger con el estado remoto cuando se recupere la conexión.
- **FR-011**: La interfaz MUST ofrecer objetivos táctiles de al menos 48dp, etiquetas semánticas comprensibles y contraste mínimo WCAG AA.
- **FR-012**: Las transiciones del badge, apertura del centro y retirada de una fila MUST respetar la preferencia de movimiento reducido del sistema operativo.
- **FR-013**: El centro de avisos financieros (PNOT) MUST permanecer separado de la bandeja de revisión de capturas/importaciones (P19).

### Key Entities

- **Aviso**: Mensaje dirigido a una cuenta, con título, cuerpo, tipo, fecha de emisión, estado de lectura y estado de retirada.
- **Referencia de destino**: Vínculo opcional y débil a una tarjeta, presupuesto, deuda o meta; relaciona tipo e identificador sin asumir que el objeto seguirá existiendo.
- **Estado de interacción**: Estado de lectura y retirada que el usuario puede cambiar y que debe conservarse para su cuenta.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Desde la campana, el usuario llega al centro y ve la última lista local en menos de 2 segundos en el dispositivo de referencia.
- **SC-002**: En todos los escenarios automatizados, el contador coincide exactamente con los avisos activos no leídos de la cuenta seleccionada; los avisos retirados no cuentan.
- **SC-003**: El 100% de las pruebas de navegación para destinos presentes llega al objeto indicado y el 100% de los casos sin destino mantiene el centro visible sin excepción.
- **SC-004**: Las pruebas de aislamiento confirman que ningún aviso ni acción de otra cuenta aparece tras cambiar de cuenta.
- **SC-005**: Las pruebas de interacción confirman que lectura y retirada se reflejan en la copia local de inmediato y se sincronizan después de recuperar conectividad.
- **SC-006**: Todos los controles accionables cumplen el objetivo táctil mínimo establecido por el producto y no dependen exclusivamente del color o del movimiento para comunicar estado.

## Assumptions

- La sesión autenticada de HU-01 aporta el identificador de cuenta activo; HU-42 no implementa autenticación.
- PNOT es el centro financiero de HU-42. P19 sigue siendo la bandeja de revisión de elementos importados y no es el destino de la campana.
- La pantalla de detalle de tarjeta existe en el repositorio. Presupuesto, deuda y meta pertenecen a historias posteriores; mientras su destino no esté registrado, se muestra el mensaje de indisponibilidad de forma segura.
- «BILLING_DUE» se presenta como recordatorio. Los tipos de condición ocurrida y los avisos de sistema se presentan como alertas mientras no exista otra regla de clasificación aprobada.
- `app_notifications` no define una columna dedicada de vencimiento. Cuando el productor la conoce, la fecha prevista se entrega en `event_payload.due_date` como `YYYY-MM-DD`; si falta, el texto preformateado del productor en `body` es la fuente visible. El cliente etiqueta `BILLING_DUE` como fecha prevista, conserva `body`, no deriva vencimientos de `created_at` ni presenta un recordatorio como pago realizado.
- Los nombres de tipos y referencias aceptan mayúsculas de productores existentes y minúsculas de documentación; el modelo de presentación los normaliza sin reescribir avisos históricos.
- Al no existir una instrucción normativa para marcar automáticamente un aviso al fallar su destino, el fallo de navegación no cambia su estado de lectura.
- La hora de vencimiento se presenta como fecha prevista del recordatorio y no como confirmación de pago.

## Incremento Sprint 6: HU-44 — Recordatorios y deduplicación

**Historia**: Como usuario de Kipu, quiero configurar la anticipación de los recordatorios de pago y evitar avisos repetidos para atender obligaciones sin confundirlos con pagos realizados.

**Estimación**: 5 puntos | **Sprint**: S6 | **Épica**: EP-NOT.

**Dependencias bloqueantes**: HU-42, HU-09, HU-29 y HU-33. **Relacionadas**: HU-34, HU-35 y HU-40.

### User Scenarios & Testing — HU-44

#### User Story 4 — Recordar vencimientos sin duplicados (Priority: P1)

Como usuario, quiero recibir un recordatorio configurable antes del vencimiento de una deuda o recurrencia, y encontrarlo en el centro de notificaciones aunque Android no permita mostrar notificaciones del sistema.

**Independent Test**: Programar una deuda y una recurrencia, verificar la anticipación predeterminada de dos días y un valor configurable; repetir la reconciliación sin duplicar el aviso; cambiar la fecha o reanudar con prórroga, pausar/cancelar la regla y ejecutar con permiso concedido, denegado y revocado.

**Acceptance Scenarios**:

1. **Anticipación configurable** — Dada una deuda o recurrencia activa con vencimiento, cuando no hay un valor explícito, entonces el recordatorio se programa dos días antes; cuando el usuario cambia la anticipación, se usa el nuevo valor.
2. **Reintento idempotente** — Dado un recordatorio ya programado o emitido, cuando se repite la reconciliación o el worker reintenta con la misma fuente, tipo y fecha de vencimiento, entonces permanece un solo evento activo y un solo aviso del sistema pendiente.
3. **Cambio de fecha o prórroga** — Dado un recordatorio pendiente y una fecha de vencimiento corregida o prorrogada, cuando se guarda el cambio, entonces se cancela la programación anterior, se retira lógicamente cualquier aviso local anterior ya emitido y queda una sola programación para la fecha vigente.
4. **Regla pausada o cancelada** — Dada una recurrencia en estado `PAUSED` o `CANCELLED`, cuando se alcanza una fecha previamente programada o se reintenta un worker atrasado, entonces no se emite recordatorio. Una deuda no activa tampoco genera recordatorios.
5. **Permiso Android denegado o revocado** — Dado que el usuario no permite `POST_NOTIFICATIONS`, o lo revoca antes de la entrega, cuando vence la anticipación, entonces el recordatorio queda consultable en el centro HU-42 del dispositivo; si el permiso está concedido, también puede mostrarse como notificación Android.
6. **Sin efecto financiero** — Dado que el usuario recibe o abre un recordatorio, cuando consulta sus movimientos y obligaciones, entonces el recordatorio no crea ni confirma un pago o movimiento financiero.

### Functional Requirements — HU-44

- **FR-014**: Para cada deuda o recurrencia activa con vencimiento, el sistema MUST programar un recordatorio con anticipación configurable en días y un valor predeterminado de 2 días.
- **FR-015**: La identidad idempotente MUST incluir propietario, `source_id`, `event_type` y `due_date`; repetir el mismo evento MUST conservar un único recordatorio activo y no duplicar el trabajo ni la notificación del sistema.
- **FR-016**: Al cambiar el vencimiento o registrar una prórroga, el sistema MUST cancelar el trabajo anterior, retirar lógicamente el evento local previamente generado si existe y programar únicamente la fecha vigente.
- **FR-017**: Antes de entregar un recordatorio, el sistema MUST verificar propietario, fecha vigente y estado de la fuente; recurrencias `PAUSED`/`CANCELLED` y deudas no activas MUST quedar suprimidas, incluso ante trabajo atrasado o reintento.
- **FR-018**: La denegación o revocación de `POST_NOTIFICATIONS` MUST impedir solo la publicación Android; MUST conservar el recordatorio consultable en el centro HU-42 del dispositivo.
- **FR-019**: Los recordatorios MUST ser informativos y MUST NOT registrar, confirmar ni modificar movimientos, pagos, saldos o historial financiero.
- **FR-020**: La programación MUST basarse en la fecha civil del vencimiento y no prometer ejecución exacta al segundo; una demora del planificador Android no cambia la fecha de vencimiento ni implica pago.

### Success Criteria — HU-44

- **SC-007**: Las pruebas confirman que el valor predeterminado es de 2 días y que un valor configurable vigente determina la fecha programada.
- **SC-008**: En el 100% de las pruebas de reintento para una misma tupla de propietario, `source_id`, `event_type` y `due_date`, queda un único recordatorio activo.
- **SC-009**: En el 100% de los cambios de vencimiento o prórrogas, el trabajo de la fecha anterior no emite después del cambio y solo existe una programación vigente.
- **SC-010**: Ninguna prueba con recurrencia pausada/cancelada o deuda no activa produce un recordatorio visible del sistema o del centro.
- **SC-011**: Con `POST_NOTIFICATIONS` denegado o revocado, el recordatorio sigue disponible en el centro interno del dispositivo y no crea efectos financieros.

### Assumptions — HU-44

- La anticipación se configura por regla/obligación mediante los valores existentes del dominio, no mediante una nueva preferencia global. Se conservan los límites ya validados por tipo; un valor explícito `0` desactiva el recordatorio, consistente con el copy actual de deuda.
- `source_id` identifica la fuente del vencimiento: la regla de recurrencia para sus ocurrencias, la cuota para una deuda con cuotas y la deuda para un vencimiento único. `event_type` usa el tipo de recordatorio `BILLING_DUE`; `due_date` se serializa como fecha ISO `YYYY-MM-DD`.
- Los vencimientos existentes sin valor explícito usan el valor predeterminado de 2 días; un valor `0` explícito se conserva como desactivado.
- Los recordatorios de HU-44 se guardan como eventos locales Room del centro HU-42 en el dispositivo y no se insertan ni sincronizan en `public.app_notifications`; se conserva la prohibición HU-42 de INSERT desde el cliente. No se promete sincronización del evento local entre dispositivos ni recuperación tras perder los datos de la aplicación.
- Si el permiso está concedido, la notificación del sistema es una presentación adicional del mismo evento local; al reprogramar, se cancela/reemplaza también la notificación anterior.
- El planificador usa la zona `America/Lima` y la fecha civil de vencimiento. WorkManager/Android puede diferir la entrega; no se garantiza una hora exacta.

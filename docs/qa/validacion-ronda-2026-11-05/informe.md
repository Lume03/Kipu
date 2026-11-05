# Validación funcional Fase 5 — Samsung A16

Fecha: 2026-11-05 (America/Lima). Dispositivo USB RFGL70J9E2T, com.kipu.app, versionName 0.1.0 / versionCode 1, actualizada 13:41:11. Se utilizó la release instalada indicada por el coordinador; estos metadatos no identifican por sí solos el commit ni acreditan R8.

Ejecución mediante ADB del SDK, input tap/text/swipe guiado por uiautomator. Cada evidencia numerada citada dispone de PNG (`exec-out screencap -p`) y XML (`uiautomator dump`); los nombres describen la intención de la captura, pero su contenido y este informe determinan el resultado. Hay capturas intermedias de navegación que no alcanzaron el destino pretendido: no se usan como prueba de aprobación. SELECT exclusivamente mediante Management API; el PAT se leyó en memoria desde config.toml y no se incluyó en archivos ni salida.

Privacidad: las capturas y XML contienen datos de la cuenta real del Owner. Son evidencia local pendiente de revisión de privacidad, ignorada por Git mediante .gitignore; no publicar ni adjuntar sin revisión. No hubo commits, instalación, desinstalación, pm clear, logout, cambios de plan ni pruebas instrumentadas. No se tocó el S24 ni las escalas de animación. Modo avión final: 0.

## Matriz de resultados

| Escenario | Resultado | Evidencia | Observaciones |
|---|---|---|---|
| 1. Cuenta + débito offline, cierre forzado y recuperación | PASS | [Creación](05-cuenta-creada.png), [reinicio](16-reinicio-offline.png), [débito 0505 persistente](18-debito-reinicio.xml), [SELECT](24-remoto-sync.json) | QA-F5-Cuenta, saldo inicial S/100; cuenta y tarjeta con el mismo alias, una fila remota cada una. La tarjeta se creó junto a la cuenta. |
| 1. Presupuesto offline y recuperación | PASS | [Creación](15-presupuesto-creado.png), [persistencia](21-presupuesto-reinicio.png), [SELECT](24-remoto-sync.json) | QA-F5-Presupuesto, mensual, límite S/10; una sola fila remota antes de limpiar. |
| 1. Gasto offline → force-stop → sync | PASS, segunda ejecución controlada | [Guardado](91-gasto2-guardado.png), [tras reinicio aún offline](92-reinicio-gasto2.png), [SELECT confirmado](107-gasto2-remoto.json), [detalle sincronizado](108-gasto2-anular.png) | QA-F5-Gasto2, S/1, Alimentación, QA-F5-Cuenta. La consulta inicial devolvió cero; varios minutos después, una sola fila CONFIRMED. Se anuló después para limpieza. |
| 2. BUG-003: anular desde detalle, incluso saldada | PASS | [Saldada](32-deuda-saldada.png), [historial](34-historial-deuda.png), [confirmación](35-anular-pago.png), [reabierta](37-reabierta.png) | QA-F5-Deuda S/2, pago total desde cuenta QA. Tras anular: Activa, pendiente S/2, principal resuelto S/0. |
| 2. BUG-003: anular desde movimiento | PASS | [Movimiento](45-movimiento-pago.png), [acción](46-anulacion-movimiento.png), [confirmación](47-confirmacion-movimiento.png), [saldo restaurado](49-lista-deudas.png), [SELECT final](124-remoto-operaciones.json) | Segundo pago S/1 desde registro rápido, anulado en historial global. Deuda pendiente S/2. Remoto: pagos S/2 y S/1 VOIDED, una fila por importe; no son duplicados entre sí. |
| 3. HU-24: Gasto → deuda | PASS con límite de evidencia | [Selección](42-quick-deuda.png), [principal](43-quick-redirect.png), [movimiento](45-movimiento-pago.png), [SELECT](124-remoto-operaciones.json) | Pago S/1 clasificado DEBT_PAYMENT; no STANDARD operativo. No se capturó el saldo intermedio S/1 de esta segunda operación antes de anular; el cambio y reversión de saldo están acreditados para el pago total del escenario 2. |
| 3. HU-24: Ingreso → préstamo | PASS | [Antes S/2](51-prestamo-creado.png), [principal](54-prestamo-principal.png), [después S/1](55-prestamo-saldo.png), [SELECT](124-remoto-operaciones.json) | Cobro S/1, 50% cobrado, DEBT_PAYMENT. Posteriormente anulado para limpieza. |
| 3. HU-24: Transferencia → meta | PASS | [Aplicar](60-aplicar-meta.png), [traslado](63-traslado-meta.png), [reserva 10%](64-meta-detalle.png), [SELECT](124-remoto-operaciones.json) | QA-F5-Cuenta → Efectivo S/1; reserva en QA-F5-Meta S/1 de S/10, GOAL_FUNDING. No STANDARD operativo. La nota automática no permite prefijo: «Transferencia explícita para reserva de meta»; se identifica por la cuenta QA y la meta QA. |
| 4. Calendario → recurrencia | PASS | [Evento del 30/11](78-evento-30.png), [abrir recurrencia](79-evento-destino.png), [destino](80-recurrencia-destino.png) | Calendario → Spotify previsto → Abrir recurrencia → Mis recurrencias, Spotify Activa. Solo lectura, sin pausar ni cancelar. |
| 4. Notificaciones → deuda/meta | NO EJECUTADO | [Centro](82-notificaciones.png) | Solo existían tres alertas del presupuesto QA; no había aviso de deuda o meta. |
| 5. Premium: Manual / Notificación / OCR | PASS para aplicación UI; datos parciales | [Premium](65-filtros.png), [opciones](67-fuente-opciones.png), [Manual](68-filtro-manual.png), [Notificación](71-filtro-notificacion.png), [OCR solo](74-filtro-ocr-solo.png) | Manual devuelve movimientos; Notificación y OCR activan sus chips y muestran Sin resultados. No hay fixtures de esas fuentes para acreditar su clasificación positiva. Se retiraron los filtros al finalizar. |
| 5. Free: bloqueo | NO EJECUTADO | [Plan actual](84-planes.png), [selector](85-elegir-plan.png) | Premium sin vencimiento; el selector visible ofrece confirmar plan comercial, sin control dev inequívoco. No se confirmó ni cambió el plan real. |
| 6. Metas: déficit y opciones | NO EJECUTADO | [Sin metas iniciales](13-planificacion.png), [respaldo QA sin déficit](64-meta-detalle.png) | No había datos de déficit. La meta QA tenía S/1 reservado con saldo real suficiente. No se alteraron fondos del Owner para provocar déficit. |

## Pasos y límites de la ejecución offline

1. Activar `cmd connectivity airplane-mode enable`; crear cuenta bancaria QA-F5-Cuenta, débito Visa 0505, gasto QA-F5-Gasto S/1 y presupuesto QA-F5-Presupuesto S/10.
2. `am force-stop com.kipu.app`, reabrir con launcher; comprobar cuenta, saldo y débito; presupuesto comprobado al volver a Planificación. Desactivar modo avión y consultar remoto.
3. El primer gasto apareció finalmente Anulado en local y ausente de remoto ([detalle](39-gasto-anulado.png)); no hay evidencia suficiente de la causa de esa anulación. Esa primera ejecución NO acredita gasto sincronizado y no se atribuye como fallo reproducible de la app.
4. Repetir solo el gasto como QA-F5-Gasto2 offline, force-stop y reapertura: persistió pendiente ([XML](92-reinicio-gasto2.xml)). Recuperar conexión: [primera consulta](98-gasto2-remoto.json) vacía, [consulta posterior](107-gasto2-remoto.json) con una fila CONFIRMED. El [detalle](108-gasto2-anular.xml) muestra Sincronizado. No se considera fallo la demora observada.
5. Tras obtener evidencia remota, anular gasto2 y limpiar los demás fixtures por UI.

## Limpieza y datos que permanecen

- QA-F5-Cuenta: saldo corregido a cero con «Corregir saldo inicial»; archivada. Se conserva su apertura y compensación en el historial. [Saldo cero](120-inicial-cero.png), [archivada](122-cuenta-archivada.png), [SELECT](125-remoto-limpieza.json).
- Débito QA-F5-Cuenta / 0505: permanece en remoto, is_archived=false y deleted_at nulo, vinculado a la cuenta archivada. No se encontró acción independiente de eliminación del débito en el formulario de cuenta; no se presume borrado en cascada.
- QA-F5-Deuda S/2 y QA-F5-Prestamo S/2: permanecen activas como obligaciones previas, sin efecto de apertura en caja. Pagos S/2 y S/1 y cobro S/1 anulados. La app impide eliminarlas por tener movimientos: [Historial protegido](96-eliminar-prestamo.png). No se condonaron ni ajustaron obligaciones para ocultar el historial.
- QA-F5-Meta: reserva retirada íntegramente y eliminada del listado; remoto conserva la fila en estado ARCHIVED ([SELECT](126-meta-estado.json)). Historial de aporte/retiro conservado.
- QA-F5-Presupuesto: eliminado por UI ([confirmación](105-eliminar-presupuesto.png)); ausente en SELECT final. Permanecen tres avisos QA de umbrales 50/80/100%, sin acción visible para eliminarlos.
- QA-F5-Gasto y QA-F5-Gasto2: anulados localmente; gasto2 confirmado VOIDED remoto. El primero no apareció en las consultas remotas.
- Traslado GOAL_FUNDING S/1: el detalle no ofrece Anular/Editar ([captura](113-transferencia-expandida.png)). Se compensó con Efectivo → QA-F5-Cuenta S/1, nota QA-F5-Limpieza-retorno; ambos quedan en historial. La nota del primer traslado la generó automáticamente la app sin prefijo, pese a usar cuenta y meta QA.
- Total disponible final S/579.31, BCP S/370.71, Efectivo S/208.60: iguales a los iniciales. [Inicial](01-dinero.png), [final](122-cuenta-archivada.png). No queda dinero QA reservado. La retención de deudas sí agrega obligaciones de prueba a las listas y se informa explícitamente.

La última consulta no acredita todas las compensaciones de apertura/retorno en remoto; sí acredita los VOIDED de pagos y gasto2, y el archivo de la cuenta. No se confunde limpieza local con convergencia remota completa.

## Hallazgos, sin correcciones

- **UI-F5-01 — FAIL visual en detalle de pago de deuda:** «OPERACIÓN DE DEUDA» y el título reciben una columna extremadamente estrecha, ocupan gran altura y desplazan el contenido. [PNG](45-movimiento-pago.png), [XML](45-movimiento-pago.xml): etiqueta con bounds `[248,322][275,883]` (27 px de ancho). La anulación funciona; el defecto es de presentación.
- **Observación de limpieza:** las deudas con pagos anulados siguen protegidas contra eliminación. Se registran como remanentes, no como regresión funcional sin contrastar el contrato.
- **Observación de trazabilidad:** el traslado explícito a meta genera una nota genérica sin conservar QA-F5 en la nota; se identifica por cuenta y meta. No hay acción de anular en su detalle observado. No se afirma incumplimiento de requisito de producto no contrastado.

Scripts locales `qa.py` y `remote_query.py` documentan los mecanismos de captura y consulta; no contienen credenciales. `manifest-sha256.tsv` contiene huellas de evidencia. La aprobación es por escenario y con los límites indicados: no se emite aprobación global de la ronda.

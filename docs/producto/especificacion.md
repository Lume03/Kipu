# KIPU V4.2 - Especificación del Producto

> **Integración documental — 2026-10-20.** Base de producto trasladada desde `KipuApp/Kipu md/01_Kipu_V4.2_Especificacion_Producto.md`. Consultar el [índice y autoridad](../README.md), los refinamientos aprobados de [specs/](../../specs/) y las [diferencias pendientes](../control-documental.md). Las metas y contratos de este documento no certifican implementación o despliegue.

> **Alcance, reglas transversales, organización Scrum, arquitectura de alto nivel, SDD con Spec Kit y criterios de beta. El detalle operativo de las HU vive en el Product Backlog.**

| Base | Decisión |
| :--- | :--- |
| **Versión** | Kipu V4.2 |
| **Épicas** | 12 épicas |
| **Historias de Usuario** | 60 Historias de Usuario |
| **Planificación** | 10 sprints |
| **Equipo** | 9 integrantes |

---

## 0. Control de versión

Esta edición reorganiza la documentación V4.2 para trabajar con Scrum usando las Historias de Usuario como unidad funcional principal. El catálogo previo de requisitos deja de formar parte del flujo operativo; las reglas transversales y de negocio se conservan en lenguaje directo y verificable.

---

## 1. Producto y alcance conservado

Kipu centraliza cuentas, tarjetas, gastos, ingresos, transferencias, deudas, préstamos, metas, recurrencias y presupuestos. La app registra hechos financieros y ayuda a planificar; no mueve dinero en bancos, no ejecuta pagos ni ofrece crédito.

El núcleo es Android nativo, local-first, con Room/SQLite y sincronización mediante Supabase. El registro manual permanece disponible sin conexión después de una sesión previa. La captura por notificaciones compatibles y el OCR local de imágenes Yape complementan el registro, sin sustituirlo ni prometer cobertura universal.

| Incluido | Fuera de alcance |
| :--- | :--- |
| Login por correo, biometría opcional, privacidad y sincronización | iOS, finanzas compartidas y Open Banking |
| Ledger único, cuotas, pagos, reembolsos y planificación | Pagos bancarios reales, lectura de SMS o correos |
| Captura compatible, Yape híbrido, OCR local y bandeja | IA generativa, predicciones o insights nuevos |
| Free, Trial, mensual, anual y Lifetime | Publicidad y personalización visual Premium nueva |
| CSV/JSON para portabilidad personal | Reportes PDF/Excel dentro de la app, tags y cuarto tipo Aporte |

### 1.1 Planes, cupos y funcionamiento offline

| Capacidad | Free | Premium / Trial vigente |
| :--- | :--- | :--- |
| Instrumentos activos | 4; efectivo y Cuenta Metas virtual aparte | Sin límite comercial |
| Categorías personalizadas raíz | 5 de gastos + 5 de ingresos; subcategorías heredan | Sin límite comercial |
| Metas / deudas / presupuestos | 2 activos por grupo | Sin límite comercial |
| Historial manual, sync e iconos ordinarios | Disponibles | Disponibles |
| Analítica | Básica del mes actual | Históricos, comparaciones y filtros combinados |
| Nueva captura y OCR | No; alternativa manual | Sí, con permisos y fuente compatible |

La concesión Premium offline se acota al menor valor entre última verificación + 72 horas y el fin de acceso conocido. Tras caducar sin red, se suspende nueva captura/OCR y otras acciones Premium sensibles, pero continúan consulta y registro manual local. Reconectar sincroniza pendientes y revalida el permiso.

### 1.2 CRUD y conservación

| Objeto | Edición / cierre | Eliminación |
| :--- | :--- | :--- |
| Meta | Editar sin perder aportes. Completar conserva reserva. Archivo requiere liberar o reasignar. | Física solo sin operaciones; con historia, archivo. |
| Deuda / préstamo | Editar metadatos. Principal solo mediante corrección auditable. Cierre con cero o ajuste explícito. | Física solo sin operaciones vinculadas; con historia, cierre/archivo. |
| Presupuesto | Editar, finalizar y renovar automáticamente por periodo. Sin Pausar/Reanudar. | Física de definición y periodos con confirmación; no borra movimientos. |
| Movimiento | Editar con revisión y revertir efectos consistentemente. | Lógica/anulación; conserva relaciones. |
| Recurrencia | Pausar, reanudar o cancelar sin gastos ficticios. | Conserva historia al cancelar. |

| Acuerdo | Cambio normativo | HU |
| :--- | :--- | :--- |
| AC-01 | Metas: edición, eliminación física solo sin operaciones; con historia liberar/reasignar y archivar. | HU-30, HU-32 |
| AC-02 | Deudas/préstamos: edición y cierre con cero o ajuste auditable; no borrar físicamente si hay operaciones vinculadas. | HU-26 a HU-29 |
| AC-03 | Presupuestos: editar, finalizar y eliminar físicamente definición/periodos con advertencia; no borrar movimientos. Sin pausa/reanudación manual. | HU-38 |
| AC-04 | Después de hasta 72 h sin revalidación continúan registro y consulta manual; acciones Premium sensibles requieren validar de nuevo. | HU-59 |

---

## 2. Organización Scrum y responsables de épica

Los nueve integrantes forman parte del equipo de desarrollo. Product Owner y Scrum Master son responsabilidades de Scrum adicionales, no excusas para retirar a esas personas del trabajo técnico. Para coordinación interna se usa “Responsable de Épica”, que no es un rol oficial de Scrum y no implica propiedad exclusiva de las HU.

| Épica | Nombre | HU | Responsable de épica | Foco base |
| :--- | :--- | :--- | :--- | :--- |
| EP-APS | Acceso, Perfil y Seguridad | HU-01 a HU-06 | Integrante 9 | Desarrollo de pruebas, integración y herramientas |
| EP-CTA | Cuentas y Tarjetas | HU-07 a HU-13 | Integrante 6 | Desarrollo backend y sincronización |
| EP-CCO | Categorías, Subcategorías y Comercios | HU-14 a HU-17 | Integrante 2 | Desarrollo de interfaces y consultas |
| EP-MOV | Movimientos y Ledger | HU-18 a HU-25 | Integrante 4 | Desarrollo de reglas financieras |
| EP-DEU | Deudas y Préstamos | HU-26 a HU-29 | Integrante 5 | Desarrollo de persistencia local |
| EP-MET | Metas y Reservas Virtuales | HU-30 a HU-32 | Integrante 3 | Desarrollo de planificación y formularios |
| EP-REC | Recurrencias y Obligaciones | HU-33 a HU-35 | Integrante 7 | Desarrollo de eventos y avisos |
| EP-PRE | Presupuestos y Consumos | HU-36 a HU-38 | Integrante 3 | Desarrollo de planificación y formularios |
| EP-INF | Inicio e Información Financiera | HU-39 a HU-41 | Integrante 2 | Desarrollo de interfaces y consultas |
| EP-NOT | Centro de Notificaciones y Avisos | HU-42 a HU-44 | Integrante 7 | Desarrollo de eventos y avisos |
| EP-AUT | Captura Automática e Import Inbox | HU-45 a HU-51 | Integrante 8 | Desarrollo de captura y normalización |
| EP-PLA | Planes, Límites y Monetización Freemium | HU-52 a HU-60 | Integrante 1 | Desarrollo Android y planes |

Distribución resultante: Integrantes 2, 3 y 7 coordinan dos épicas; los otros seis coordinan una. La cantidad de HU de una épica no equivale a carga individual, porque implementación, revisión, pruebas e integración se reparten por Sprint Planning.

El detalle completo de las 60 Historias de Usuario, sus reglas, dependencias y criterios Gherkin se mantiene en el [Product Backlog](backlog.md). El orden entre historias se documenta en [Trazabilidad y Dependencias](trazabilidad-y-dependencias.md) y en el [Cronograma Scrum V4.1 para el producto V4.2](../planificacion/cronograma-scrum.md).

---

## 3. Reglas financieras y calidad

| ID | Regla conservada |
| :--- | :--- |
| RN-01 | La línea de crédito no es dinero disponible y no se suma al patrimonio. |
| RN-02 | Patrimonio neto = activos reales + cuentas por cobrar - pasivos pendientes. Las reservas de metas no se suman de nuevo. |
| RN-03 | Los saldos cambian por eventos y ajustes auditables, nunca por edición silenciosa. |
| RN-04 | Importes en unidades menores (Long/BIGINT) y código de moneda ISO 4217. No usar Float/Double para dinero. |
| RN-05 | Transferir entre cuentas reales tiene efectos vinculados atómicos; no aumenta ingresos ni gastos. |
| RN-06 | La compra con crédito es gasto cuando ocurre. El pago posterior reduce cuenta y pasivo; no crea otro gasto. |
| RN-07 | Pagar principal de una deuda desde Gasto reduce obligación y cuenta, pero se excluye de gastos operativos. Intereses reales separados sí pueden ser gasto. |
| RN-08 | Cobrar principal prestado desde Ingreso aumenta cuenta y reduce por cobrar; se excluye de ingresos operativos. |
| RN-09 | Recibir un préstamo aumenta dinero y deuda sin crear ingreso operativo. |
| RN-10 | Prestar dinero reduce cuenta y crea por cobrar sin crear gasto operativo. |
| RN-11 | Aportar a una meta es asignación/reserva, opcionalmente acompañada de transferencia real representada en Kipu; no es gasto ni ingreso. |
| RN-12 | Anulaciones se excluyen de métricas. Reembolsos totales/parciales quedan vinculados al movimiento original. |
| RN-13 | Presupuestos consumen gastos confirmados; excluyen pendientes, transferencias, pagos de principal, aportes y anulaciones. |
| RN-14 | Un gasto puede consumir presupuesto de subcategoría y de su padre; el total cuenta ese gasto una sola vez. |
| RN-15 | Una compra a cuotas reconoce su principal una vez. Cuotas e intereses estimados no generan gastos reales por sí mismos. |
| RN-16 | Cada captura y cada comando de escritura tienen identidad estable para evitar duplicados. |
| RN-17 | Toda compra detectada con tarjeta de crédito requiere confirmación, aunque la confianza sea alta. |
| RN-18 | Texto fuente, comercio canónico y evidencia mínima se distinguen; la trazabilidad no exige guardar imágenes indefinidamente. |

### 3.1 Reglas no funcionales y objetivos de calidad

| ID | Objetivo de validación |
| :--- | :--- |
| RNF-01 | Offline: crear, editar y consultar núcleo local sin red; compras, primer login y restauración remota necesitan conexión. |
| RNF-02 | UI: objetivo p95 menor a 300 ms para consultas locales simples con dataset de 10,000 movimientos, en dispositivo de referencia acordado S1. |
| RNF-03 | Persistencia: cerrar proceso después de guardar no pierde ni deja incompleta la transacción confirmada. |
| RNF-04 | Sync: reintentos no duplican datos; UUID, comandos estables y revisión. |
| RNF-05 | Seguridad financiera: nunca PAN completo, CVV, contraseña bancaria o token de pago bancario. |
| RNF-06 | Privacidad: permisos contextuales y revocables; captura apagada en Free. |
| RNF-07 | Aislamiento: RLS impide lectura/escritura cruzada entre dos usuarios, incluyendo relaciones hijas. |
| RNF-08 | Logs: sin tokens, montos/comercios sensibles, notificaciones o comprobantes de producción. |
| RNF-09 | Accesibilidad: contraste AA, áreas táctiles de 48 dp, TalkBack y aumento de fuente sin perder controles. |
| RNF-10 | Usabilidad: movimiento manual común en menos de 10 segundos en una tarea conocida con datos preparados; medir con personas, no afirmarlo sin prueba. |
| RNF-11 | Batería: eventos + WorkManager; sin polling agresivo ni recordatorios por segundo. |
| RNF-12 | Compatibilidad: fijar minSdk/targetSdk S1 y probar al menos dos dispositivos Android reales del equipo. |
| RNF-13 | Mantenibilidad: dominio financiero independiente de Android, OCR, Play y Supabase. |
| RNF-14 | Testabilidad: reglas de dinero y acceso deterministas con reloj/repositorios sustituibles. |
| RNF-15 | Observabilidad: códigos y métricas agregadas para fallos de parser, sync y compras, sin datos sensibles. Las cifras de rendimiento/usabilidad son metas de aceptación, no resultados medidos del producto. En S1 se registra dispositivo, versión Android, volumen de datos y procedimiento; S10 compara contra esa referencia. |
| RNF-16 | Migraciones Room/Postgres versionadas y verificadas desde la versión anterior; sin migración destructiva para liberar cupos. |
| RNF-17 | Recuperación E2E: offline → sync → segundo dispositivo con misma cuenta. |
| RNF-18 | OCR fallido permite edición manual; no bloquea registrar un movimiento propio. |
| RNF-19 | Sugerencias indican origen de regla/historial/catálogo y confianza. |
| RNF-20 | Dataset financiero de referencia coincide con saldos, presupuestos y reportes. |
| RNF-21 | · V4 Compra verificada, vinculación única, acknowledgement reintentable y revocación reconciliada. |
| RNF-22 | · V4 Cambio de plan no pierde ni reclasifica datos; mismo resultado tras reinstalación y sync. |
| RNF-23 | · V4 Concesión offline acotada; renovar por reloj manipulado o reinstalación no funciona. |
| RNF-24 | Dominio, plataforma y seguridad |

---

## 4. Arquitectura y desarrollo

Stack base: Kotlin, Jetpack Compose Material 3, Hilt, Coroutines/StateFlow, Room/SQLite, DataStore, WorkManager, Kotlinx Serialization, cliente Kotlin de Supabase, ML Kit y Google Play Billing. La plataforma es Android nativo; no se cambia a Expo.

| Capa | Responsabilidad | Salida verificable |
| :--- | :--- | :--- |
| Presentación | Pantallas, ViewModel y estado observable; no calcula dinero. | Estados vacío/error/offline y navegación. |
| Dominio | Casos de uso, Money, efectos, reglas de acceso y dedupe. | Pruebas deterministas sin Android/proveedor. |
| Persistencia local | Room, relaciones, revisiones y outbox atómica. | Reapertura sin pérdidas ni medias operaciones. |
| Sync/backend | Identidad, aislamiento, comandos, conflictos y compras. | Dos dispositivos, RLS y reintentos seguros. |
| Captura | Listener, Share Intent, OCR, parser y candidato. | No ledger hasta resolución autorizada. |

Secuencia común de escritura: la UI emite un comando con identidad estable; el dominio valida propietario, importe, moneda, referencias, plan y duplicados; una transacción local guarda evento, efectos y outbox; la UI observa el estado sin esperar red; WorkManager sincroniza con backoff y el servidor aplica controles de identidad/revisión. ConfirmCandidate reutiliza RegisterTransaction.

| Contrato Kipu | Garantía |
| :--- | :--- |
| RegisterTransaction / ConfirmCandidate | Evento y efectos atómicos, idempotencia, acceso y revisión. |
| FeatureAccessPolicy | Allowed/Denied con motivo; misma política en UI y dominio. |
| /billing/verify y /entitlements/me | Compra remota verificada y concesión temporal del propietario. |
| /plans/selection | Selección versionada y cupos sin borrar excedentes. |
| /billing/rtdn | Emisor autenticado, consulta de estado real y dedupe de evento. |
| SyncPush / SyncPull | Comandos estables, control de versión y respuesta de conflicto. |

---

## 5. SDD con Spec Kit y Scrum

Scrum organiza objetivos semanales, colaboración, inspección y adaptación. Spec-Driven Development con GitHub Spec Kit orienta la implementación desde una especificación verificable. El backlog usa HU agrupadas en épicas; las HU, reglas y dependencias conservan trazabilidad. El Responsable de Épica verifica coherencia de su dominio, pero el equipo decide quién toma cada tarea del sprint.

| Paso | Artefacto / salida |
| :--- | :--- |
| 1. Especificar | spec.md: valor, reglas, HU, escenarios, dependencias y aceptación. |
| 2. Planificar | plan.md / research.md / data-model.md / contracts según corresponda. |
| 3. Descomponer | tasks.md: tareas, dependencias, archivos, criterio y evidencia. |
| 4. Implementar | Código, migraciones, PR pequeños e integración continua. |
| 5. Verificar | Tests, revisión cruzada, demo y evidencia. |
| 6. Actualizar | Spec, trazabilidad, ADR y backlog cuando cambie una regla. |

### 5.1 Definition of Done común

- La HU entrega su resultado integrado; pasan sus escenarios Gherkin y regresiones afectadas.
- Código revisado por otro integrante y build/lint/pruebas aprobados; sin defectos bloqueantes.
- Persistencia, migración y aislamiento probados cuando se alteran datos; cálculos financieros conciliados.
- Estados de error, vacío, offline y permisos cubiertos; accesibilidad revisada en pantallas afectadas.
- Pruebas de proveedor/dispositivo reales cuando corresponde; fixtures identificados como simulaciones.
- Documento, reglas y dependencias actualizados. Trabajo parcial no suma una HU aceptada.

---

## 6. Cobertura mínima de beta

| Caso | Cobertura |
| :--- | :--- |
| TC-01 | Crédito: compra, cuotas y pago sin doble gasto |
| TC-02 | Préstamo recibido sin ingreso operativo |
| TC-03 | Préstamo otorgado y cobro sin gasto/ingreso por principal |
| TC-04 | Transferencia atómica y reintentos |
| TC-05 | Metas: reserva, liberación, edición y archivo |
| TC-06 | Presupuestos: jerarquía, renovación, reembolso y eliminación física |
| TC-07 | Recurrencias: activa/pausada/cancelada y esperados |
| TC-08 | Captura: confianza y confirmación obligatoria de crédito |
| TC-09 | Yape: ambos órdenes y dos pagos reales iguales |
| TC-10 | Downgrade: cupos y conservación integral |
| TC-11 | Compra: pendiente, verificación, reconocimiento y restauración |
| TC-12 | RTDN: duplicados, desorden y reconciliación |
| TC-13 | Offline: 72 horas, reloj, revalidación y núcleo manual |
| TC-14 | Sincronización: conflicto, borrados y dos dispositivos |
| TC-15 | Seguridad, privacidad, accesibilidad y migraciones |
| TC-16 | Beta: build instalable, rendimiento, regresión y demo |

---

## 7. Fuentes y control documental

Fuente normativa de origen: Kipu_Especificacion_Integral_V4(1).pdf (06/09/2026). Base inmediata: Kipu_V4.1.pdf y Cronograma_Scrum_Kipu_9_Integrantes.pdf (11/09/2026). V4.2 conserva el alcance funcional y reorganiza la especificación alrededor de HU específicas, dependencias explícitas y responsabilidad fija solo a nivel de épica.

Regla para futuras revisiones: mantener estables los identificadores HU. Si se divide una HU, registrar sucesoras y trasladar escenarios sin duplicar puntos. Todo cambio de alcance, política de eliminación, precio objetivo, regla de negocio, dependencia o sprint debe reflejarse en la documentación y el cronograma vigente.

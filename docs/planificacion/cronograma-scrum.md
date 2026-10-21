# KIPU V4.2 - Cronograma Scrum V4.1

> **Integración documental — 2026-10-20.** Plan base emitido el 12/09/2026 para el producto V4.2, con horizonte 09/09–17/11/2026. V4.1 es la versión del cronograma, no evidencia de que esté reemplazado. Se conserva como planificación de referencia, no como registro de cumplimiento. Contrastar el estado real con [specs/](../../specs/) y [QA](../qa/README.md). Ver [backlog](../producto/backlog.md), [trazabilidad](../producto/trazabilidad-y-dependencias.md) y [diferencias pendientes](../control-documental.md).

> **Diez sprints, responsables fijos de épica, HU específicas, dependencias explícitas, orden interno, Spec Kit y trabajo de los nueve integrantes.**

| Base de producto | Plan de trabajo |
| :--- | :--- |
| **Kipu V4.2:** 12 épicas / 60 HU específicas | 12 épicas / 60 HU / 10 sprints |
| **Android nativo y funcionamiento local-first** | 9 integrantes / 20 horas por semana |
| **Versión del cronograma:** V4.1 - 12/09/2026 | **Desarrollo:** 09/09/2026 al 17/11/2026 |

**Cambio clave:** se elimina la idea de “responsable fijo de HU”. Solo se fija un Responsable de Épica. La distribución semanal mostrada es una propuesta de Sprint Planning y puede reajustarse por el equipo sin romper dependencias ni Sprint Goal.

---

## 1. Cómo leer y ejecutar este cronograma

Cada HU tiene un único sprint de cierre. Las dependencias Bloqueantes determinan qué debe existir antes de aceptar una HU; las Parciales permiten adelantar UI, contratos o fixtures, pero no cerrar la integración; las Relacionadas no bloquean y se cubren mediante regresión cruzada.

Cadencia: miércoles Review/Retrospectiva del ciclo anterior y Planning del siguiente; jueves-viernes implementación; lunes integración E2E; martes validación y preparación de Review. Cada integrante dispone de 20 h/semana: 12 h desarrollo, 4 h eventos y coordinación, 4 h reserva de integración/defectos.

| Capacidad | Por integrante / semana | Equipo / semana | 10 sprints |
| :--- | :--- | :--- | :--- |
| Desarrollo comprometido | 12 h | 108 h | 1080 h |
| Eventos y coordinación | 4 h | 36 h | 360 h |
| Reserva integración/defectos | 4 h | 36 h | 360 h |
| Total | 20 h | 180 h | 1800 h |

---

## 2. Responsables de épica

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

El Responsable de Épica mantiene contexto de las HU, reglas y dependencias y participa en refinamiento/aceptación técnica de esa épica. No decide solo ni debe implementar todas sus HU. Los nueve siguen desarrollando en los diez sprints.

### 2.1 Regla Spec Kit por épica

Cuando una épica tenga trabajo comprometido en un sprint, su Responsable de Épica debe crear o actualizar el archivo spec.md de esa épica antes de cerrar la implementación. El spec es parte del entregable del sprint y no una documentación posterior.

Primera aparición de la épica: CREAR el spec.md. Apariciones posteriores: ACTUALIZAR el mismo spec.md con las HU, reglas, dependencias, decisiones y criterios que entren en ese sprint. No se crea un spec distinto por persona ni se duplica la fuente de verdad.

Contenido mínimo obligatorio del spec.md: objetivo y alcance de la épica; HU incluidas en el sprint; dependencias bloqueantes/parciales/relacionadas; reglas de negocio; criterios Gherkin; datos y entidades afectadas; contratos/API si aplican; comportamiento offline/sincronización; acceso Free/Premium cuando aplique; riesgos y decisiones abiertas; enlaces a plan.md, tasks.md, ADR, pruebas y PR.

Regla de aceptación: una HU puede estar codificada, pero el incremento de la épica no se considera documentalmente completo si su spec.md no refleja lo realmente implementado y las decisiones tomadas durante el sprint. Si el spec introduce o modifica una entidad, relación, restricción, índice, política RLS o regla de sincronización, el Responsable de Épica debe actualizar también el documento Modelado de Datos Kipu V4.2 (y el data-model.md técnico si se genera para esa feature) dentro del mismo sprint.

---

## 3. Roadmap y cierre único de historias

| Sprint / fechas | Historias | Pts | Objetivo |
| :--- | :--- | :--- | :--- |
| S1 09/09/2026 - 15/09/2026 | HU-01, HU-02, HU-03, HU-04, HU-05, HU-52 | 24 | Kipu arranca con identidad y privacidad. |
| S2 16/09/2026 - 22/09/2026 | HU-07, HU-08, HU-14, HU-15, HU-18, HU-19, HU-23, HU-57 | 57 | Kipu registra dinero desde cuentas propias. |
| S3 23/09/2026 - 29/09/2026 | HU-09, HU-10, HU-11, HU-12, HU-13, HU-42, HU-53, HU-54, HU-56 | 55 | Kipu distingue crédito y valida compras. |
| S4 30/09/2026 - 06/10/2026 | HU-20, HU-21, HU-22, HU-58, HU-59 | 31 | Kipu corrige movimientos y opera offline. |
| S5 07/10/2026 - 13/10/2026 | HU-16, HU-17, HU-26, HU-27, HU-28, HU-29, HU-55 | 52 | Kipu controla obligaciones y conserva derechos de compra. |
| S6 14/10/2026 - 20/10/2026 | HU-24, HU-30, HU-31, HU-32, HU-33, HU-35, HU-44 | 45 | Kipu organiza metas y recurrencias. |
| S7 21/10/2026 - 27/10/2026 | HU-25, HU-36, HU-37, HU-38, HU-39, HU-40 | 42 | Kipu controla presupuestos, concilia reembolsos y muestra su situación. |
| S8 28/10/2026 - 03/11/2026 | HU-34, HU-43, HU-45, HU-46, HU-47, HU-48, HU-50, HU-51 | 49 | Kipu captura y permite revisar antes de registrar. |
| S9 04/11/2026 - 10/11/2026 | HU-06, HU-41, HU-49 | 29 | Kipu completa Yape y recupera sus datos. |
| S10 11/11/2026 - 17/11/2026 | HU-60 | 8 | Kipu entrega una beta integrada y verificable. |

Único cambio de cierre respecto del cronograma anterior: HU-25 pasa de S4 a S7. Motivo: HU-25 depende de la base presupuestaria creada por HU-36 para corregir el consumo del presupuesto al registrar un reembolso. Con el cambio no queda ninguna dependencia bloqueante situada en un sprint posterior a la HU dependiente.

---

## Sprint 1 | 09/09/2026 - 15/09/2026

**Sprint Goal:** Kipu arranca con identidad y privacidad.

**Entrega completa / finalidad (MVP 1):** Al cierre debe quedar operativo el acceso base de Kipu: registro e inicio de sesión, recuperación y persistencia de sesión, desbloqueo biométrico opcional, preferencias de privacidad, gestión inicial de permisos y elección entre Free o prueba Premium. El usuario debe poder entrar y utilizar la app aunque rechace permisos de automatización.

**Capacidad:** 9 integrantes x 20 h = 180 h; 108 h de desarrollo comprometido, 36 h de eventos y 36 h de reserva.

| HU | Bloqueantes | Épica / resp. | Pts |
| :--- | :--- | :--- | :--- |
| HU-01 Registro e inicio de sesión | Ninguna | EP-APS / I9 | 5 |
| HU-02 Recuperación y ciclo de sesión | HU-01 | EP-APS / I9 | 5 |
| HU-03 Desbloqueo local con biometría | HU-01 | EP-APS / I9 | 3 |
| HU-04 Preferencias y privacidad de saldos | HU-01 | EP-APS / I9 | 3 |
| HU-05 Permisos y fuentes de automatización | HU-01 | EP-APS / I9 | 3 |
| HU-52 Elegir Free o prueba Premium | HU-01 | EP-PLA / I1 | 5 |

*Total estimado: 24 puntos. Los puntos son esfuerzo relativo; no equivalen a horas.*

### Orden recomendado por dependencias

**Primero:** HU-01 (Registro e inicio de sesión)

**Después:** HU-02 (Recuperación y ciclo de sesión), HU-03 (Desbloqueo local con biometría), HU-04 (Preferencias y privacidad de saldos), HU-05 (Permisos y fuentes de automatización), HU-52 (Elegir Free o prueba Premium)

**Integración parcial en paralelo:** HU-05 ↔ HU-52. Pueden avanzar con contrato/fixture, pero se cierran integradas.

**Entrada:** repositorio, diseño base y cuentas de desarrollo disponibles.

**Riesgo y tratamiento:** Correo/enlace de recuperación no configurado: probar antes de prometer flujo completo; preservar errores neutros.

**Qué se demuestra:** Registrar una cuenta de test; recuperar acceso con correo existente e inexistente; reabrir sesión; ocultar saldos; rechazar permisos y seguir usando la app. Elegir Free sin método de pago. La oferta Trial es informativa, no un permiso ficticio.

### Sprint 1 | Trabajo de los nueve integrantes

Distribución sugerida para Sprint Planning. No crea responsables fijos de HU: el equipo puede intercambiar tareas manteniendo dependencias, especialidad, capacidad y Sprint Goal. Cada fila cubre 12 h de desarrollo; las 4 h de eventos y 4 h de reserva son comunes.

| Integrante | Épica(s) que coordina | Trabajo sugerido del sprint | Carga |
| :--- | :--- | :--- | :--- |
| Integrante 1 | EP-PLA | HU-01/02/52: implementar registro, recuperación y elección Free; entregar navegación autenticada y enlace de recuperación probado. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 2 | EP-CCO / EP-INF | HU-04: desarrollar preferencias y MoneyText; entregar máscara persistente y estados de error/accesibilidad. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 3 | EP-MET / EP-PRE | HU-01/04: desarrollar formularios reutilizables de correo, contraseña y fechas; entregar validadores y componentes usados por acceso. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 4 | EP-MOV | HU-01/02/52: implementar estado de sesión e intención de plan sin conceder Premium; entregar reglas deterministas y casos de cuenta equivocada. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 5 | EP-DEU | HU-01/04: crear partición local por usuario y persistencia de preferencias; entregar dato que sobrevive cierre de proceso. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 6 | EP-CTA | HU-01/02: integrar identidad remota y recuperación; entregar aislamiento inicial y limpieza de sesión con aviso de pendientes. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 7 | EP-REC / EP-NOT | HU-03/05: implementar biometría opcional y configuración de permisos; entregar rechazo/revocación sin bloquear uso manual. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 8 | EP-AUT | HU-05/04: desarrollar selector de fuentes y estado de permiso no concedido; entregar fixtures sin activar lectura ni OCR. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 9 | EP-APS | HU-01/02/03/04: programar pruebas de acceso y reinicio, automatizar build/lint; entregar pipeline y reporte reproducible. | 12 h dev + 4 h eventos + 4 h reserva |

### Sprint 1 | Integración, pruebas y Review

**Responsables de épica involucrados:** EP-APS: Integrante 9, EP-PLA: Integrante 1.

**Spec Kit del Sprint 1: entrega del Responsable de Épica**

- **CREAR** - EP-APS | Acceso, Perfil y Seguridad | Integrante 9: `specs/001-ep-aps-acceso-perfil-seguridad/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **CREAR** - EP-PLA | Planes, Límites y Monetización Freemium | Integrante 1: `specs/012-ep-pla-planes-monetizacion/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.

El Responsable de Épica coordina este artefacto, pero el equipo puede colaborar en su redacción y evidencias. La implementación puede repartirse libremente durante Sprint Planning/Daily; el spec mantiene la fuente de verdad funcional de la épica.

Cada integrante descompone sus 12 h de desarrollo en implementación, pruebas del componente e integración/revisión. Las HU se aceptan solo cuando sus criterios Gherkin y dependencias bloqueantes están satisfechos. Revisar regresiones de reglas compartidas, RLS, dinero, sincronización y acceso por plan cuando el sprint toque esos componentes.

**Guion de Review:** 5 min de objetivo e historias; 25 min de demo con al menos un error/recuperación; 10 min de comprobación de criterios y dependencias; 5 min de feedback y pendientes. No mostrar solo pantallas: demostrar persistencia, efectos y reglas.

**Definition of Done:** integrado, revisado, pruebas verdes, datos coherentes, sin bloqueantes, estados/offline/privacidad cubiertos y trazabilidad Épica↔HU↔dependencias actualizada. Una HU parcial no suma puntos aceptados; spec.md de cada épica involucrada creado/actualizado, consistente con las HU, reglas, dependencias y evidencia del sprint.

---

## Sprint 2 | 16/09/2026 - 22/09/2026

**Sprint Goal:** Kipu registra dinero desde cuentas propias.

**Entrega completa / finalidad (MVP 2):** Al cierre debe quedar funcionando el núcleo financiero básico: administración de cuentas e instrumentos, categorías/subcategorías y comercios, registro manual de movimientos, actualización coherente de saldos, prevención de duplicados y aplicación de cupos/selección del plan. Debe ser posible registrar dinero propio sin duplicar el saldo ni dejar operaciones parciales.

**Capacidad:** 9 integrantes x 20 h = 180 h; 108 h de desarrollo comprometido, 36 h de eventos y 36 h de reserva.

| HU | Bloqueantes | Épica / resp. | Pts |
| :--- | :--- | :--- | :--- |
| HU-07 Administración de cuentas | HU-01, HU-57 | EP-CTA / I6 | 5 |
| HU-08 Registro y vínculo de tarjetas | HU-07, HU-57 | EP-CTA / I6 | 5 |
| HU-14 Categorías y subcategorías | HU-01, HU-57 | EP-CCO / I2 | 8 |
| HU-15 Comercios y servicios | HU-01 | EP-CCO / I2 | 5 |
| HU-18 Registro manual de movimientos | HU-07, HU-14 | EP-MOV / I4 | 8 |
| HU-19 Saldos inmediatos y atomicidad | HU-18 | EP-MOV / I4 | 8 |
| HU-23 Detección de duplicados | HU-18 | EP-MOV / I4 | 5 |
| HU-57 Cupos, selección y downgrade | HU-01, HU-52 | EP-PLA / I1 | 13 |

*Total estimado: 57 puntos. Los puntos son esfuerzo relativo; no equivalen a horas.*

### Orden recomendado por dependencias

**Primero:** HU-15 (Comercios y servicios), HU-57 (Cupos, selección y downgrade)

**Después:** HU-07 (Administración de cuentas), HU-14 (Categorías y subcategorías)

**Luego:** HU-08 (Registro y vínculo de tarjetas), HU-18 (Registro manual de movimientos)

**Integración final:** HU-19 (Saldos inmediatos y atomicidad), HU-23 (Detección de duplicados)

**Integración parcial en paralelo:** HU-15 ↔ HU-18, HU-18 ↔ HU-15. Pueden avanzar con contrato/fixture, pero se cierran integradas.

**Entrada:** incremento S1 integrado y sus pruebas verdes; accesos de proveedor necesarios confirmados.

**Riesgo y tratamiento:** Atomicidad y cupos concurrentes: impedir medias transferencias; sin pruebas de persistencia no aceptar ledger.

**Qué se demuestra:** Crear banco S/100 y un débito vinculado; comprobar que no se suma dos veces. Crear categoría, registrar gasto S/20 offline y transferencia entre cuentas. Reiniciar y reenviar comando: el saldo es coherente y no hay duplicado. Intentar exceder cupo.

### Sprint 2 | Trabajo de los nueve integrantes

Distribución sugerida para Sprint Planning. No crea responsables fijos de HU: el equipo puede intercambiar tareas manteniendo dependencias, especialidad, capacidad y Sprint Goal. Cada fila cubre 12 h de desarrollo; las 4 h de eventos y 4 h de reserva son comunes.

| Integrante | Épica(s) que coordina | Trabajo sugerido del sprint | Carga |
| :--- | :--- | :--- | :--- |
| Integrante 1 | EP-PLA | HU-57/18: integrar control de cupos al guardado y shell financiero; entregar errores de límite con salida útil. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 2 | EP-CCO / EP-INF | HU-15/18: implementar selector de comercio y Gasto/Ingreso/Transferencia; entregar flujo manual completo offline. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 3 | EP-MET / EP-PRE | HU-07/08/14: implementar altas de cuentas, tarjetas y categorías; entregar formularios con jerarquía y cupos. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 4 | EP-MOV | HU-19/23/57: desarrollar efectos atómicos, dedupe y política 4/5/2/2/2; entregar motor probado con fixtures de todos los grupos. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 5 | EP-DEU | HU-07/18/19: crear tablas, DAO y outbox transaccional; entregar cierre de proceso sin medias transferencias. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 6 | EP-CTA | HU-07/18/57: desarrollar push/pull inicial y validación de cupo por usuario; entregar sync de una cuenta y un movimiento entre dos sesiones propias. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 7 | EP-REC / EP-NOT | HU-18/23: implementar validadores de instrumento y fecha, y protección de doble toque; entregar reintentos sin duplicación. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 8 | EP-AUT | HU-14/15: cargar catálogo inicial y desarrollar búsqueda normalizada del selector; entregar casos sin coincidencias y sin tags. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 9 | EP-APS | HU-19/23/57: programar pruebas de dinero en céntimos, relaciones débito/cuenta y concurrencia; entregar golden dataset inicial. | 12 h dev + 4 h eventos + 4 h reserva |

### Sprint 2 | Integración, pruebas y Review

**Responsables de épica involucrados:** EP-CTA: Integrante 6, EP-CCO: Integrante 2, EP-MOV: Integrante 4, EP-PLA: Integrante 1.

**Spec Kit del Sprint 2: entrega del Responsable de Épica**

- **CREAR** - EP-CTA | Cuentas y Tarjetas | Integrante 6: `specs/002-ep-cta-cuentas-tarjetas/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **CREAR** - EP-CCO | Categorías, Subcategorías y Comercios | Integrante 2: `specs/003-ep-cco-categorias-comercios/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **CREAR** - EP-MOV | Movimientos y Ledger | Integrante 4: `specs/004-ep-mov-movimientos-ledger/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **ACTUALIZAR** - EP-PLA | Planes, Límites y Monetización Freemium | Integrante 1: `specs/012-ep-pla-planes-monetizacion/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.

El Responsable de Épica coordina este artefacto, pero el equipo puede colaborar en su redacción y evidencias. La implementación puede repartirse libremente durante Sprint Planning/Daily; el spec mantiene la fuente de verdad funcional de la épica.

Cada integrante descompone sus 12 h de desarrollo en implementación, pruebas del componente e integración/revisión. Las HU se aceptan solo cuando sus criterios Gherkin y dependencias bloqueantes están satisfechos. Revisar regresiones de reglas compartidas, RLS, dinero, sincronización y acceso por plan cuando el sprint toque esos componentes.

**Guion de Review:** 5 min de objetivo e historias; 25 min de demo con al menos un error/recuperación; 10 min de comprobación de criterios y dependencias; 5 min de feedback y pendientes. No mostrar solo pantallas: demostrar persistencia, efectos y reglas.

**Definition of Done:** integrado, revisado, pruebas verdes, datos coherentes, sin bloqueantes, estados/offline/privacidad cubiertos y trazabilidad Épica↔HU↔dependencias actualizada. Una HU parcial no suma puntos aceptados; spec.md de cada épica involucrada creado/actualizado, consistente con las HU, reglas, dependencias y evidencia del sprint.

---

## Sprint 3 | 23/09/2026 - 29/09/2026

**Sprint Goal:** Kipu distingue crédito y valida compras.

**Entrega completa / finalidad (MVP 3):** Al cierre debe quedar operativo el manejo de crédito y la primera integración real de monetización: línea y saldo de crédito, umbrales, catálogo/tasas, compras en cuotas, pago de tarjeta, notificaciones relacionadas y compra mensual/anual/Lifetime con verificación de Google Play y estados de compra. Premium solo se habilita cuando la compra válida ha sido verificada.

**Capacidad:** 9 integrantes x 20 h = 180 h; 108 h de desarrollo comprometido, 36 h de eventos y 36 h de reserva.

| HU | Bloqueantes | Épica / resp. | Pts |
| :--- | :--- | :--- | :--- |
| HU-09 Línea y saldos de crédito | HU-08, HU-18, HU-19 | EP-CTA / I6 | 8 |
| HU-10 Umbrales de crédito | HU-09 | EP-CTA / I6 | 3 |
| HU-11 Catálogo y tasas referenciales | HU-08 | EP-CTA / I6 | 5 |
| HU-12 Pago de tarjeta | HU-07, HU-09, HU-18, HU-19 | EP-CTA / I6 | 5 |
| HU-13 Compras con crédito y cuotas | HU-08, HU-18, HU-19, HU-23 | EP-CTA / I6 | 8 |
| HU-42 Centro de notificaciones | HU-01 | EP-NOT / I7 | 5 |
| HU-53 Compra mensual, anual y Lifetime | HU-52 | EP-PLA / I1 | 8 |
| HU-54 Verificación y reconocimiento de compras | HU-01, HU-52 | EP-PLA / I1 | 8 |
| HU-56 Estados de compra y cancelación | HU-54 | EP-PLA / I1 | 5 |

*Total estimado: 55 puntos. Los puntos son esfuerzo relativo; no equivalen a horas.*

### Orden recomendado por dependencias

**Primero:** HU-09 (Línea y saldos de crédito), HU-11 (Catálogo y tasas referenciales), HU-13 (Compras con crédito y cuotas), HU-42 (Centro de notificaciones), HU-53 (Compra mensual, anual y Lifetime), HU-54 (Verificación y reconocimiento de compras)

**Después:** HU-10 (Umbrales de crédito), HU-12 (Pago de tarjeta), HU-56 (Estados de compra y cancelación)

**Integración parcial en paralelo:** HU-10 ↔ HU-42, HU-13 ↔ HU-11, HU-53 ↔ HU-54, HU-54 ↔ HU-53. Pueden avanzar con contrato/fixture, pero se cierran integradas.

**Entrada:** incremento S2 integrado y sus pruebas verdes; accesos de proveedor necesarios confirmados.

**Riesgo y tratamiento:** Canal Play y cuentas de licencia no disponibles: simulaciones no sustituyen compra verificada; registrar bloqueo visible.

**Qué se demuestra:** Comprar con crédito S/100 en tres cuotas sin interés; mostrar 33.34/33.33/33.33. Registrar pago y comprobar que no vuelve a contar como gasto. Ejecutar compra de test en Play y verificar Premium; compra pendiente no habilita. Mostrar alerta de crédito.

### Sprint 3 | Trabajo de los nueve integrantes

Distribución sugerida para Sprint Planning. No crea responsables fijos de HU: el equipo puede intercambiar tareas manteniendo dependencias, especialidad, capacidad y Sprint Goal. Cada fila cubre 12 h de desarrollo; las 4 h de eventos y 4 h de reserva son comunes.

| Integrante | Épica(s) que coordina | Trabajo sugerido del sprint | Carga |
| :--- | :--- | :--- | :--- |
| Integrante 1 | EP-PLA | HU-53: desarrollar compra de mensual/anual/Lifetime en canal de test; integrar respuesta verificada, no un bypass de UI. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 2 | EP-CCO / EP-INF | HU-09/11: desarrollar detalle de crédito, línea y simulación; entregar separación de dinero y crédito con fechas válidas. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 3 | EP-MET / EP-PRE | HU-11/13: implementar configuración de tasas y formulario directo/cuotas; entregar fuente/fecha de catálogo y redondeo visible. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 4 | EP-MOV | HU-09/12/13: desarrollar compra, amortización y reparto de céntimos; entregar casos S/100 en tres cuotas y pago sin doble gasto. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 5 | EP-DEU | HU-09/13/56: crear persistencia de planes de cuotas y caché de permisos; entregar migración compatible con movimientos existentes. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 6 | EP-CTA | HU-54/56: desarrollar verificación, reconocimiento idempotente y estado de compra; entregar PENDING, rechazo de token ajeno y cancelada vigente. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 7 | EP-REC / EP-NOT | HU-10/42: desarrollar umbrales y centro de avisos con deep link; entregar cruce único y detalle de tarjeta. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 8 | EP-AUT | HU-13/54: desarrollar adaptadores/fixtures de compra y candidato de crédito para pruebas; entregar confianza alta que nunca omite confirmación. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 9 | EP-APS | HU-12/13/53/54: programar pruebas integradas de crédito y compra de test; entregar evidencia de proveedor y regresión del ledger. | 12 h dev + 4 h eventos + 4 h reserva |

### Sprint 3 | Integración, pruebas y Review

**Responsables de épica involucrados:** EP-CTA: Integrante 6, EP-NOT: Integrante 7, EP-PLA: Integrante 1.

**Spec Kit del Sprint 3: entrega del Responsable de Épica**

- **ACTUALIZAR** - EP-CTA | Cuentas y Tarjetas | Integrante 6: `specs/002-ep-cta-cuentas-tarjetas/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **CREAR** - EP-NOT | Centro de Notificaciones y Avisos | Integrante 7: `specs/010-ep-not-notificaciones-avisos/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **ACTUALIZAR** - EP-PLA | Planes, Límites y Monetización Freemium | Integrante 1: `specs/012-ep-pla-planes-monetizacion/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.

El Responsable de Épica coordina este artefacto, pero el equipo puede colaborar en su redacción y evidencias. La implementación puede repartirse libremente durante Sprint Planning/Daily; el spec mantiene la fuente de verdad funcional de la épica.

Cada integrante descompone sus 12 h de desarrollo en implementación, pruebas del componente e integración/revisión. Las HU se aceptan solo cuando sus criterios Gherkin y dependencias bloqueantes están satisfechos. Revisar regresiones de reglas compartidas, RLS, dinero, sincronización y acceso por plan cuando el sprint toque esos componentes.

**Guion de Review:** 5 min de objetivo e historias; 25 min de demo con al menos un error/recuperación; 10 min de comprobación de criterios y dependencias; 5 min de feedback y pendientes. No mostrar solo pantallas: demostrar persistencia, efectos y reglas.

**Definition of Done:** integrado, revisado, pruebas verdes, datos coherentes, sin bloqueantes, estados/offline/privacidad cubiertos y trazabilidad Épica↔HU↔dependencias actualizada. Una HU parcial no suma puntos aceptados; spec.md de cada épica involucrada creado/actualizado, consistente con las HU, reglas, dependencias y evidencia del sprint.

---

## Sprint 4 | 30/09/2026 - 06/10/2026

**Sprint Goal:** Kipu corrige movimientos y opera offline.

**Entrega completa / finalidad (MVP 4):** Al cierre debe quedar completo el mantenimiento seguro del ledger y la operación local: editar movimientos, anularlos o aplicar borrado lógico, buscar y filtrar, aplicar capacidades Free/Premium y respetar concesiones offline con expiración. Los cambios deben conservar integridad financiera y sincronizarse al reconectar sin duplicar operaciones.

**Capacidad:** 9 integrantes x 20 h = 180 h; 108 h de desarrollo comprometido, 36 h de eventos y 36 h de reserva.

| HU | Bloqueantes | Épica / resp. | Pts |
| :--- | :--- | :--- | :--- |
| HU-20 Edición de movimientos | HU-18, HU-19 | EP-MOV / I4 | 5 |
| HU-21 Anulación y borrado lógico | HU-18, HU-19 | EP-MOV / I4 | 5 |
| HU-22 Búsqueda y filtros | HU-18 | EP-MOV / I4 | 5 |
| HU-58 Acceso Free y Premium por capacidad | HU-52, HU-57 | EP-PLA / I1 | 8 |
| HU-59 Concesión offline y expiración | HU-54, HU-58 | EP-PLA / I1 | 8 |

*Total estimado: 31 puntos. Los puntos son esfuerzo relativo; no equivalen a horas.*

### Orden recomendado por dependencias

**Primero:** HU-20 (Edición de movimientos), HU-21 (Anulación y borrado lógico), HU-22 (Búsqueda y filtros), HU-58 (Acceso Free y Premium por capacidad)

**Después:** HU-59 (Concesión offline y expiración)

**Entrada:** incremento S3 integrado y sus pruebas verdes; accesos de proveedor necesarios confirmados.

**Riesgo y tratamiento:** Conflictos financieros y reloj local: revisión explícita; no aplicar última escritura ni extender concesión por fecha manipulada.

**Qué se demuestra:** Corregir gasto, anular transferencia y registrar devolución parcial con fecha distinta. Mostrar filtros básicos Free y rechazo de avanzada no autorizada. Simular concesión caducada y registrar manualmente; reconectar sin duplicados.

### Sprint 4 | Trabajo de los nueve integrantes

Distribución sugerida para Sprint Planning. No crea responsables fijos de HU: el equipo puede intercambiar tareas manteniendo dependencias, especialidad, capacidad y Sprint Goal. Cada fila cubre 12 h de desarrollo; las 4 h de eventos y 4 h de reserva son comunes.

| Integrante | Épica(s) que coordina | Trabajo sugerido del sprint | Carga |
| :--- | :--- | :--- | :--- |
| Integrante 1 | EP-PLA | HU-58: integrar políticas de acceso Free/Premium a filtros y entradas protegidas; entregar rechazo de acceso indebido y mensajes de capacidad. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 2 | EP-CCO / EP-INF | HU-20/22: desarrollar edición y búsqueda/filtros; entregar estados de conflicto, filtros combinables y limpieza de filtros. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 3 | EP-MET / EP-PRE | HU-21/22: implementar diálogo de anulación y componentes de filtros; entregar confirmación destructiva y navegación coherente. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 4 | EP-MOV | HU-20/21: desarrollar correcciones atómicas y reversión de efectos; entregar saldos conciliados tras editar o anular. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 5 | EP-DEU | HU-20/21/59: implementar revisiones, tombstones y concesión local; entregar reinicio offline con correcciones persistentes. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 6 | EP-CTA | HU-59/20: implementar revalidación remota y resolución de conflictos financieros; entregar recuperación de red sin reescritura silenciosa. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 7 | EP-REC / EP-NOT | HU-59/58: desarrollar agenda de revalidación y suspensión de acciones Premium caducadas; entregar núcleo manual operativo tras 72 horas. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 8 | EP-AUT | HU-23/22: ampliar fixtures de deduplicación y consultas; entregar casos con operaciones iguales legítimas y filtros reproducibles. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 9 | EP-APS | HU-20/21/22/59: programar pruebas de reloj, desconexión, edición y reintento; entregar matriz de resultados con fallos reproducibles. | 12 h dev + 4 h eventos + 4 h reserva |

### Sprint 4 | Integración, pruebas y Review

**Responsables de épica involucrados:** EP-MOV: Integrante 4, EP-PLA: Integrante 1.

**Spec Kit del Sprint 4: entrega del Responsable de Épica**

- **ACTUALIZAR** - EP-MOV | Movimientos y Ledger | Integrante 4: `specs/004-ep-mov-movimientos-ledger/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **ACTUALIZAR** - EP-PLA | Planes, Límites y Monetización Freemium | Integrante 1: `specs/012-ep-pla-planes-monetizacion/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.

El Responsable de Épica coordina este artefacto, pero el equipo puede colaborar en su redacción y evidencias. La implementación puede repartirse libremente durante Sprint Planning/Daily; el spec mantiene la fuente de verdad funcional de la épica.

Cada integrante descompone sus 12 h de desarrollo en implementación, pruebas del componente e integración/revisión. Las HU se aceptan solo cuando sus criterios Gherkin y dependencias bloqueantes están satisfechos. Revisar regresiones de reglas compartidas, RLS, dinero, sincronización y acceso por plan cuando el sprint toque esos componentes.

**Guion de Review:** 5 min de objetivo e historias; 25 min de demo con al menos un error/recuperación; 10 min de comprobación de criterios y dependencias; 5 min de feedback y pendientes. No mostrar solo pantallas: demostrar persistencia, efectos y reglas.

**Definition of Done:** integrado, revisado, pruebas verdes, datos coherentes, sin bloqueantes, estados/offline/privacidad cubiertos y trazabilidad Épica↔HU↔dependencias actualizada. Una HU parcial no suma puntos aceptados; spec.md de cada épica involucrada creado/actualizado, consistente con las HU, reglas, dependencias y evidencia del sprint.

---

## Sprint 5 | 07/10/2026 - 13/10/2026

**Sprint Goal:** Kipu controla obligaciones y conserva derechos de compra.

**Entrega completa / finalidad (MVP 5):** Al cierre debe quedar operativo el módulo de obligaciones: deudas propias, dinero prestado, pagos/cobros de principal, cuotas, vencimientos y cierre de deudas, junto con alias y preferencias personales asociadas. También debe quedar funcionando la reconciliación y restauración de derechos de compra sin duplicar activaciones ni alterar incorrectamente ingresos/gastos.

**Capacidad:** 9 integrantes x 20 h = 180 h; 108 h de desarrollo comprometido, 36 h de eventos y 36 h de reserva.

| HU | Bloqueantes | Épica / resp. | Pts |
| :--- | :--- | :--- | :--- |
| HU-16 Texto original y alias de comercio | HU-15 | EP-CCO / I2 | 5 |
| HU-17 Preferencias personales de categoría | HU-14, HU-15 | EP-CCO / I2 | 5 |
| HU-26 Deudas propias: creación y administración | HU-07, HU-19, HU-57 | EP-DEU / I5 | 8 |
| HU-27 Dinero prestado: creación y administración | HU-07, HU-19, HU-57 | EP-DEU / I5 | 8 |
| HU-28 Pagos y cobros de principal | HU-18, HU-19, HU-26, HU-27 | EP-DEU / I5 | 8 |
| HU-29 Cuotas, vencimientos y cierre de deudas | HU-26, HU-27, HU-28 | EP-DEU / I5 | 5 |
| HU-55 Reconciliación y restauración de compras | HU-54, HU-56 | EP-PLA / I1 | 13 |

*Total estimado: 52 puntos. Los puntos son esfuerzo relativo; no equivalen a horas.*

### Orden recomendado por dependencias

**Primero:** HU-16 (Texto original y alias de comercio), HU-17 (Preferencias personales de categoría), HU-26 (Deudas propias: creación y administración), HU-27 (Dinero prestado: creación y administración), HU-55 (Reconciliación y restauración de compras)

**Después:** HU-28 (Pagos y cobros de principal)

**Luego:** HU-29 (Cuotas, vencimientos y cierre de deudas)

**Integración parcial en paralelo:** HU-17 ↔ HU-16. Pueden avanzar con contrato/fixture, pero se cierran integradas.

**Entrada:** incremento S4 integrado y sus pruebas verdes; accesos de proveedor necesarios confirmados.

**Riesgo y tratamiento:** Eventos de proveedor tardíos y cuotas históricas: consultar estado actual; no descontar desembolsos dos veces.

**Qué se demuestra:** Recibir préstamo sin ingreso operativo; prestar dinero y cobrar parte sin ingreso falso. Cerrar deuda con saldo cero y rechazar borrado con pagos. Confirmar alias personal. Repetir/desordenar RTDN y restaurar compra desde otro dispositivo.

### Sprint 5 | Trabajo de los nueve integrantes

Distribución sugerida para Sprint Planning. No crea responsables fijos de HU: el equipo puede intercambiar tareas manteniendo dependencias, especialidad, capacidad y Sprint Goal. Cada fila cubre 12 h de desarrollo; las 4 h de eventos y 4 h de reserva son comunes.

| Integrante | Épica(s) que coordina | Trabajo sugerido del sprint | Carga |
| :--- | :--- | :--- | :--- |
| Integrante 1 | EP-PLA | HU-55: integrar restauración de compra en sesión propia y mensajes de vigencia; entregar flujo de usuario en segundo dispositivo. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 2 | EP-CCO / EP-INF | HU-26/27/28: desarrollar listado/detalle de deudas y préstamos; entregar saldo, historia y pagos/cobros seleccionables. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 3 | EP-MET / EP-PRE | HU-26/27/29: implementar creación, edición, cronograma y cierre de obligaciones; entregar bloqueo de eliminación con pagos. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 4 | EP-MOV | HU-26/27/28/29: desarrollar efectos de principal e interés y cierre con saldo cero; entregar simulaciones y ajustes auditables. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 5 | EP-DEU | HU-26/28/16/17: persistir obligaciones, liquidaciones y preferencias; entregar relaciones consistentes y migración desde S4. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 6 | EP-CTA | HU-55: desarrollar RTDN autenticada, dedupe y reconciliación; entregar renovación repetida/desordenada y restauración vinculada. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 7 | EP-REC / EP-NOT | HU-29/55: desarrollar recordatorios de deudas y worker de reconciliación; entregar reintentos programados sin spam. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 8 | EP-AUT | HU-16/17: implementar alias y preferencia personal desde fixtures confirmados; entregar precedencia explícita y aislamiento por usuario. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 9 | EP-APS | HU-26/28/55: programar pruebas de préstamo recibido/otorgado, cobro, cierre y RTDN; entregar regresión de proveedor y dinero. | 12 h dev + 4 h eventos + 4 h reserva |

### Sprint 5 | Integración, pruebas y Review

**Responsables de épica involucrados:** EP-CCO: Integrante 2, EP-DEU: Integrante 5, EP-PLA: Integrante 1.

**Spec Kit del Sprint 5: entrega del Responsable de Épica**

- **ACTUALIZAR** - EP-CCO | Categorías, Subcategorías y Comercios | Integrante 2: `specs/003-ep-cco-categorias-comercios/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **CREAR** - EP-DEU | Deudas y Préstamos | Integrante 5: `specs/005-ep-deu-deudas-prestamos/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **ACTUALIZAR** - EP-PLA | Planes, Límites y Monetización Freemium | Integrante 1: `specs/012-ep-pla-planes-monetizacion/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.

El Responsable de Épica coordina este artefacto, pero el equipo puede colaborar en su redacción y evidencias. La implementación puede repartirse libremente durante Sprint Planning/Daily; el spec mantiene la fuente de verdad funcional de la épica.

Cada integrante descompone sus 12 h de desarrollo en implementación, pruebas del componente e integración/revisión. Las HU se aceptan solo cuando sus criterios Gherkin y dependencias bloqueantes están satisfechos. Revisar regresiones de reglas compartidas, RLS, dinero, sincronización y acceso por plan cuando el sprint toque esos componentes.

**Guion de Review:** 5 min de objetivo e historias; 25 min de demo con al menos un error/recuperación; 10 min de comprobación de criterios y dependencias; 5 min de feedback y pendientes. No mostrar solo pantallas: demostrar persistencia, efectos y reglas.

**Definition of Done:** integrado, revisado, pruebas verdes, datos coherentes, sin bloqueantes, estados/offline/privacidad cubiertos y trazabilidad Épica↔HU↔dependencias actualizada. Una HU parcial no suma puntos aceptados; spec.md de cada épica involucrada creado/actualizado, consistente con las HU, reglas, dependencias y evidencia del sprint.

---

## Sprint 6 | 14/10/2026 - 20/10/2026

**Sprint Goal:** Kipu organiza metas y recurrencias.

**Entrega completa / finalidad (MVP 6):** Al cierre debe quedar operativo el módulo de metas y recurrencias: crear/editar metas, reservar y liberar fondos, finalizar o archivar metas, crear y administrar recurrencias, generar recordatorios y evitar duplicados. Los movimientos deben poder aplicarse correctamente a obligaciones y metas sin convertir reservas o recordatorios en gastos falsos.

**Capacidad:** 9 integrantes x 20 h = 180 h; 108 h de desarrollo comprometido, 36 h de eventos y 36 h de reserva.

| HU | Bloqueantes | Épica / resp. | Pts |
| :--- | :--- | :--- | :--- |
| HU-24 Aplicar movimientos a obligaciones y metas | HU-18, HU-19, HU-26, HU-27, HU-30, HU-31 | EP-MOV / I4 | 8 |
| HU-30 Creación y edición de metas | HU-57 | EP-MET / I3 | 8 |
| HU-31 Aportes y reservas de metas | HU-18, HU-19, HU-30 | EP-MET / I3 | 8 |
| HU-32 Liberación, finalización y archivo de metas | HU-30, HU-31 | EP-MET / I3 | 5 |
| HU-33 Registro de recurrencias | HU-18 | EP-REC / I7 | 8 |
| HU-35 Estados de recurrencias | HU-33 | EP-REC / I7 | 3 |
| HU-44 Recordatorios y deduplicación | HU-42, HU-09, HU-29, HU-33 | EP-NOT / I7 | 5 |

*Total estimado: 45 puntos. Los puntos son esfuerzo relativo; no equivalen a horas.*

### Orden recomendado por dependencias

**Primero:** HU-30 (Creación y edición de metas), HU-33 (Registro de recurrencias)

**Después:** HU-31 (Aportes y reservas de metas), HU-35 (Estados de recurrencias), HU-44 (Recordatorios y deduplicación)

**Luego:** HU-24 (Aplicar movimientos a obligaciones y metas), HU-32 (Liberación, finalización y archivo de metas)

**Entrada:** incremento S5 integrado y sus pruebas verdes; accesos de proveedor necesarios confirmados.

**Riesgo y tratamiento:** Reservas sin respaldo y cambios de estado: mostrar insuficiencia; no liberar ni mover dinero por completar.

**Qué se demuestra:** Reservar S/200 de banco S/1000 y mostrar patrimonio intacto. Liberar S/50; completar meta sin gasto automático. Registrar recurrencia, pausar y reanudar sin crear pagos reales. Mostrar aviso y aplicar movimientos a deuda y meta.

### Sprint 6 | Trabajo de los nueve integrantes

Distribución sugerida para Sprint Planning. No crea responsables fijos de HU: el equipo puede intercambiar tareas manteniendo dependencias, especialidad, capacidad y Sprint Goal. Cada fila cubre 12 h de desarrollo; las 4 h de eventos y 4 h de reserva son comunes.

| Integrante | Épica(s) que coordina | Trabajo sugerido del sprint | Carga |
| :--- | :--- | :--- | :--- |
| Integrante 1 | EP-PLA | HU-24/30: integrar políticas de cupo a metas y operaciones asociadas; entregar selección de objeto bloqueado sin perder historia. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 2 | EP-CCO / EP-INF | HU-24/31: desarrollar selección Cuenta Metas y objetivo desde Transferencia; entregar navegación Gasto/Ingreso aplicada a deuda/préstamo. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 3 | EP-MET / EP-PRE | HU-30/33/35: implementar formularios de meta y recurrencia; entregar edición, pausa/cancelación de recurrencias y fechas. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 4 | EP-MOV | HU-24/31/32: desarrollar reserva, liberación y cierre; entregar casos de misma cuenta, traslado y respaldo insuficiente. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 5 | EP-DEU | HU-30/31/33: persistir contribuciones y reglas recurrentes; entregar rollback de aporte y partición por propietario. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 6 | EP-CTA | HU-31/32: sincronizar aportes y liberaciones como comandos atómicos; entregar rechazo de conflicto sin doble reserva. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 7 | EP-REC / EP-NOT | HU-33/35/44: implementar esperados ordinarios, anticipación y cancelación de avisos; entregar reanudación sin gastos atrasados ficticios. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 8 | EP-AUT | HU-31/32: desarrollar calculador de respaldo y fixtures de cuentas compartidas por metas; entregar falta de respaldo señalada. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 9 | EP-APS | HU-24/31/32/35: programar pruebas de metas y recurrencias; entregar ciclo aporte-liberación-archivo y no duplicación del patrimonio. | 12 h dev + 4 h eventos + 4 h reserva |

### Sprint 6 | Integración, pruebas y Review

**Responsables de épica involucrados:** EP-MOV: Integrante 4, EP-MET: Integrante 3, EP-REC: Integrante 7, EP-NOT: Integrante 7.

**Spec Kit del Sprint 6: entrega del Responsable de Épica**

- **ACTUALIZAR** - EP-MOV | Movimientos y Ledger | Integrante 4: `specs/004-ep-mov-movimientos-ledger/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **CREAR** - EP-MET | Metas y Reservas Virtuales | Integrante 3: `specs/006-ep-met-metas-reservas/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **CREAR** - EP-REC | Recurrencias y Obligaciones | Integrante 7: `specs/007-ep-rec-recurrencias-obligaciones/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **ACTUALIZAR** - EP-NOT | Centro de Notificaciones y Avisos | Integrante 7: `specs/010-ep-not-notificaciones-avisos/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.

El Responsable de Épica coordina este artefacto, pero el equipo puede colaborar en su redacción y evidencias. La implementación puede repartirse libremente durante Sprint Planning/Daily; el spec mantiene la fuente de verdad funcional de la épica.

Cada integrante descompone sus 12 h de desarrollo en implementación, pruebas del componente e integración/revisión. Las HU se aceptan solo cuando sus criterios Gherkin y dependencias bloqueantes están satisfechos. Revisar regresiones de reglas compartidas, RLS, dinero, sincronización y acceso por plan cuando el sprint toque esos componentes.

**Guion de Review:** 5 min de objetivo e historias; 25 min de demo con al menos un error/recuperación; 10 min de comprobación de criterios y dependencias; 5 min de feedback y pendientes. No mostrar solo pantallas: demostrar persistencia, efectos y reglas.

**Definition of Done:** integrado, revisado, pruebas verdes, datos coherentes, sin bloqueantes, estados/offline/privacidad cubiertos y trazabilidad Épica↔HU↔dependencias actualizada. Una HU parcial no suma puntos aceptados; spec.md de cada épica involucrada creado/actualizado, consistente con las HU, reglas, dependencias y evidencia del sprint.

---

## Sprint 7 | 21/10/2026 - 27/10/2026

**Sprint Goal:** Kipu controla presupuestos, concilia reembolsos y muestra su situación.

**Entrega completa / finalidad (MVP 7):** Al cierre debe quedar operativo el control presupuestario y la visión financiera: crear, consultar, renovar, consumir, editar, finalizar y eliminar presupuestos con historial protegido; conciliar reembolsos vinculados; y mostrar inicio/patrimonio y calendario financiero. Los consumos y devoluciones deben reflejarse correctamente sin duplicar importes ni borrar movimientos históricos.

**Capacidad:** 9 integrantes x 20 h = 180 h; 108 h de desarrollo comprometido, 36 h de eventos y 36 h de reserva.

| HU | Bloqueantes | Épica / resp. | Pts |
| :--- | :--- | :--- | :--- |
| HU-25 Reembolsos vinculados | HU-18, HU-19, HU-36 | EP-MOV / I4 | 8 |
| HU-36 Crear y consultar presupuestos | HU-14, HU-18, HU-57 | EP-PRE / I3 | 8 |
| HU-37 Renovación, consumo jerárquico y alertas | HU-36, HU-25, HU-42 | EP-PRE / I3 | 8 |
| HU-38 Edición, finalización y eliminación de presupuestos | HU-36, HU-37 | EP-PRE / I3 | 5 |
| HU-39 Inicio e historial financiero | HU-07, HU-18, HU-19 | EP-INF / I2 | 8 |
| HU-40 Calendario financiero | HU-09, HU-29, HU-33 | EP-INF / I2 | 5 |

*Total estimado: 42 puntos. Los puntos son esfuerzo relativo; no equivalen a horas.*

### Orden recomendado por dependencias

**Primero:** HU-36 (Crear y consultar presupuestos), HU-39 (Inicio e historial financiero), HU-40 (Calendario financiero)

**Después:** HU-25 (Reembolsos vinculados)

**Luego:** HU-37 (Renovación, consumo jerárquico y alertas)

**Integración final:** HU-38 (Edición, finalización y eliminación de presupuestos)

**Entrada:** incremento S6 integrado y sus pruebas verdes; accesos de proveedor necesarios confirmados.

**Riesgo y tratamiento:** Borrado físico y renovaciones duplicadas: confirmar pérdida y probar que sync no resucite objetos ni borre gastos.

**Qué se demuestra:** Crear presupuesto mensual S/500; consumir S/50 en subcategoría y padre sin duplicar total. Registrar reembolso parcial de una compra de un periodo anterior y comprobar que caja y consumo se concilian. Finalizar y eliminar presupuesto con confirmación dejando movimientos intactos. Mostrar patrimonio y calendario.

### Sprint 7 | Trabajo de los nueve integrantes

Distribución sugerida para Sprint Planning. No crea responsables fijos de HU: el equipo puede intercambiar tareas manteniendo dependencias, especialidad, capacidad y Sprint Goal. Cada fila cubre 12 h de desarrollo; las 4 h de eventos y 4 h de reserva son comunes.

| Integrante | Épica(s) que coordina | Trabajo sugerido del sprint | Carga |
| :--- | :--- | :--- | :--- |
| Integrante 1 | EP-PLA | HU-36/38: integrar cupos Free y confirmación de borrado irreversible; entregar diálogo que distingue presupuesto de movimientos. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 2 | EP-CCO / EP-INF | HU-39/40: desarrollar Inicio y calendario; entregar patrimonio con cuentas por cobrar y navegación a eventos. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 3 | EP-MET / EP-PRE | HU-36/38/25: implementar formularios de presupuesto y reembolso desde operación original; entregar límite reembolsable y edición segura. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 4 | EP-MOV | HU-25/37/38: desarrollar ajuste de gasto/presupuesto por reembolso, consumo jerárquico y renovación; entregar conciliación entre periodos. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 5 | EP-DEU | HU-36/37/38/25: persistir periodos y relaciones de reembolso; entregar prueba de movimientos intactos al eliminar presupuesto. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 6 | EP-CTA | HU-38/39/25: sincronizar borrados y reembolsos vinculados; evitar resurrección por cliente atrasado y preservar ledger. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 7 | EP-REC / EP-NOT | HU-37/40: implementar avisos de presupuesto y eventos de calendario; entregar dedupe por periodo/umbral. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 8 | EP-AUT | HU-39/37/25: desarrollar consultas de patrimonio, reservas, consumo y gasto neto; entregar golden dataset con reembolsos. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 9 | EP-APS | HU-25/36/38/39/40: programar E2E de reembolso entre periodos, cambio de periodo, borrado y TalkBack; entregar evidencias de coherencia. | 12 h dev + 4 h eventos + 4 h reserva |

### Sprint 7 | Integración, pruebas y Review

**Responsables de épica involucrados:** EP-MOV: Integrante 4, EP-PRE: Integrante 3, EP-INF: Integrante 2.

**Spec Kit del Sprint 7: entrega del Responsable de Épica**

- **ACTUALIZAR** - EP-MOV | Movimientos y Ledger | Integrante 4: `specs/004-ep-mov-movimientos-ledger/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **CREAR** - EP-PRE | Presupuestos y Consumos | Integrante 3: `specs/008-ep-pre-presupuestos-consumos/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **CREAR** - EP-INF | Inicio e Información Financiera | Integrante 2: `specs/009-ep-inf-informacion-financiera/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.

El Responsable de Épica coordina este artefacto, pero el equipo puede colaborar en su redacción y evidencias. La implementación puede repartirse libremente durante Sprint Planning/Daily; el spec mantiene la fuente de verdad funcional de la épica.

Cada integrante descompone sus 12 h de desarrollo en implementación, pruebas del componente e integración/revisión. Las HU se aceptan solo cuando sus criterios Gherkin y dependencias bloqueantes están satisfechos. Revisar regresiones de reglas compartidas, RLS, dinero, sincronización y acceso por plan cuando el sprint toque esos componentes.

**Guion de Review:** 5 min de objetivo e historias; 25 min de demo con al menos un error/recuperación; 10 min de comprobación de criterios y dependencias; 5 min de feedback y pendientes. No mostrar solo pantallas: demostrar persistencia, efectos y reglas.

**Definition of Done:** integrado, revisado, pruebas verdes, datos coherentes, sin bloqueantes, estados/offline/privacidad cubiertos y trazabilidad Épica↔HU↔dependencias actualizada. Una HU parcial no suma puntos aceptados; spec.md de cada épica involucrada creado/actualizado, consistente con las HU, reglas, dependencias y evidencia del sprint.

---

## Sprint 8 | 28/10/2026 - 03/11/2026

**Sprint Goal:** Kipu captura y permite revisar antes de registrar.

**Entrega completa / finalidad (MVP 8):** Al cierre debe quedar operativo el flujo de captura asistida desde notificaciones: activar el listener, extraer candidatos, calcular confianza, autorregistrar solo cuando corresponda, confirmar crédito detectado y gestionar pendientes en la bandeja. También deben funcionar la coincidencia con pagos esperados, alertas, evidencia mínima y aprendizaje desde correcciones, manteniendo revisión manual cuando exista ambigüedad.

**Capacidad:** 9 integrantes x 20 h = 180 h; 108 h de desarrollo comprometido, 36 h de eventos y 36 h de reserva.

| HU | Bloqueantes | Épica / resp. | Pts |
| :--- | :--- | :--- | :--- |
| HU-34 Pagos esperados y coincidencia con captura | HU-33, HU-46, HU-51 | EP-REC / I7 | 5 |
| HU-43 Alertas presupuestarias y de revisión | HU-42, HU-37, HU-51 | EP-NOT / I7 | 5 |
| HU-45 Activación del listener | HU-05, HU-58, HU-59 | EP-AUT / I8 | 5 |
| HU-46 Extracción y candidato | HU-45 | EP-AUT / I8 | 8 |
| HU-47 Confianza y autorregistro | HU-46, HU-18, HU-23 | EP-AUT / I8 | 8 |
| HU-48 Confirmación de crédito detectado | HU-46, HU-08, HU-13 | EP-AUT / I8 | 5 |
| HU-50 Aprendizaje desde correcciones | HU-16, HU-17, HU-46, HU-51 | EP-AUT / I8 | 8 |
| HU-51 Bandeja y evidencia mínima | HU-46 | EP-AUT / I8 | 5 |

*Total estimado: 49 puntos. Los puntos son esfuerzo relativo; no equivalen a horas.*

### Orden recomendado por dependencias

**Primero:** HU-45 (Activación del listener)

**Después:** HU-46 (Extracción y candidato)

**Luego:** HU-47 (Confianza y autorregistro), HU-48 (Confirmación de crédito detectado), HU-51 (Bandeja y evidencia mínima)

**Integración final:** HU-34 (Pagos esperados y coincidencia con captura), HU-43 (Alertas presupuestarias y de revisión), HU-50 (Aprendizaje desde correcciones)

**Integración parcial en paralelo:** HU-34 ↔ HU-47, HU-47 ↔ HU-51. Pueden avanzar con contrato/fixture, pero se cierran integradas.

**Entrada:** incremento S7 integrado y sus pruebas verdes; accesos de proveedor necesarios confirmados.

**Riesgo y tratamiento:** Notificaciones no compatibles o ambiguas: preservar salida manual y revisión; no adivinar instrumento.

**Qué se demuestra:** Recibir notificación compatible de débito de alta confianza y otra incierta. Revisar/confirmar/descartar desde bandeja. Crédito de alta confianza sigue pendiente. Revocar permiso, vencer Premium y verificar que no hay nueva lectura. Asociar pago recurrente sin otro gasto.

### Sprint 8 | Trabajo de los nueve integrantes

Distribución sugerida para Sprint Planning. No crea responsables fijos de HU: el equipo puede intercambiar tareas manteniendo dependencias, especialidad, capacidad y Sprint Goal. Cada fila cubre 12 h de desarrollo; las 4 h de eventos y 4 h de reserva son comunes.

| Integrante | Épica(s) que coordina | Trabajo sugerido del sprint | Carga |
| :--- | :--- | :--- | :--- |
| Integrante 1 | EP-PLA | HU-45/47: integrar permiso Premium antes de lectura y antes de guardado; entregar expiración entre captura y confirmación. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 2 | EP-CCO / EP-INF | HU-51: desarrollar bandeja con edición, confirmación y descarte; entregar candidato resuelto una sola vez y conversión manual. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 3 | EP-MET / EP-PRE | HU-48/34: integrar revisión directo/cuotas y selección de esperado recurrente; entregar campos contextuales y coincidencia ambigua. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 4 | EP-MOV | HU-47/48: integrar ConfirmCandidate con ledger único y regla de crédito; entregar autorregistro de débito sin bypass contable. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 5 | EP-DEU | HU-46/51: persistir candidatos/evidencia mínima y resoluciones; entregar transacción candidato-movimiento con idempotencia. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 6 | EP-CTA | HU-45/50: sincronizar preferencias y revisar aislamiento de candidaturas; entregar ausencia de contenido sensible en backend/logs. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 7 | EP-REC / EP-NOT | HU-45/43/34: implementar listener, aviso de revisión y vinculación de esperados; entregar fuentes autorizadas y revocación inmediata. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 8 | EP-AUT | HU-46/47/50: implementar parsers, scoring explicable y aprendizaje; entregar fixtures alta/media/baja y asociación confirmada. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 9 | EP-APS | HU-45/47/48/51: automatizar pruebas de notificación repetida, permiso revocado y candidato de crédito; entregar matriz por fuente/dispositivo. | 12 h dev + 4 h eventos + 4 h reserva |

### Sprint 8 | Integración, pruebas y Review

**Responsables de épica involucrados:** EP-REC: Integrante 7, EP-NOT: Integrante 7, EP-AUT: Integrante 8.

**Spec Kit del Sprint 8: entrega del Responsable de Épica**

- **ACTUALIZAR** - EP-REC | Recurrencias y Obligaciones | Integrante 7: `specs/007-ep-rec-recurrencias-obligaciones/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **ACTUALIZAR** - EP-NOT | Centro de Notificaciones y Avisos | Integrante 7: `specs/010-ep-not-notificaciones-avisos/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **CREAR** - EP-AUT | Captura Automática e Import Inbox | Integrante 8: `specs/011-ep-aut-captura-inbox/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.

El Responsable de Épica coordina este artefacto, pero el equipo puede colaborar en su redacción y evidencias. La implementación puede repartirse libremente durante Sprint Planning/Daily; el spec mantiene la fuente de verdad funcional de la épica.

Cada integrante descompone sus 12 h de desarrollo en implementación, pruebas del componente e integración/revisión. Las HU se aceptan solo cuando sus criterios Gherkin y dependencias bloqueantes están satisfechos. Revisar regresiones de reglas compartidas, RLS, dinero, sincronización y acceso por plan cuando el sprint toque esos componentes.

**Guion de Review:** 5 min de objetivo e historias; 25 min de demo con al menos un error/recuperación; 10 min de comprobación de criterios y dependencias; 5 min de feedback y pendientes. No mostrar solo pantallas: demostrar persistencia, efectos y reglas.

**Definition of Done:** integrado, revisado, pruebas verdes, datos coherentes, sin bloqueantes, estados/offline/privacidad cubiertos y trazabilidad Épica↔HU↔dependencias actualizada. Una HU parcial no suma puntos aceptados; spec.md de cada épica involucrada creado/actualizado, consistente con las HU, reglas, dependencias y evidencia del sprint.

---

## Sprint 9 | 04/11/2026 - 10/11/2026

**Sprint Goal:** Kipu completa Yape y recupera sus datos.

**Entrega completa / finalidad (MVP 9):** Al cierre debe quedar completo el flujo híbrido de Yape y recuperación de datos: ingresos por notificación, pagos mediante Compartir + OCR local, deduplicación entre ambas fuentes, revisión de capturas inciertas, analítica financiera y portabilidad/restauración de datos. La misma operación capturada por dos fuentes debe terminar como una sola transacción.

**Capacidad:** 9 integrantes x 20 h = 180 h; 108 h de desarrollo comprometido, 36 h de eventos y 36 h de reserva.

| HU | Bloqueantes | Épica / resp. | Pts |
| :--- | :--- | :--- | :--- |
| HU-06 Portabilidad, eliminación de cuenta y restauración | HU-01, HU-07, HU-18 | EP-APS / I9 | 8 |
| HU-41 Estadísticas y analítica financiera | HU-39, HU-36, HU-09, HU-26, HU-30, HU-58 | EP-INF / I2 | 8 |
| HU-49 Yape híbrido, OCR y deduplicación | HU-45, HU-46, HU-23, HU-58, HU-59 | EP-AUT / I8 | 13 |

*Total estimado: 29 puntos. Los puntos son esfuerzo relativo; no equivalen a horas.*

### Orden recomendado por dependencias

**Primero:** HU-06 (Portabilidad, eliminación de cuenta y restauración), HU-41 (Estadísticas y analítica financiera), HU-49 (Yape híbrido, OCR y deduplicación)

**Entrada:** incremento S8 integrado y sus pruebas verdes; accesos de proveedor necesarios confirmados.

**Riesgo y tratamiento:** OCR borroso y datos no sincronizados: no inventar campos ni prometer restauración de cambios ausentes.

**Qué se demuestra:** Procesar ingreso Yape notificado y pago compartido. Enviar ambas fuentes de la misma operación en ambos órdenes: una sola transacción. Imagen borrosa exige revisión. Exportar CSV/JSON, restaurar datos sincronizados en otro teléfono y comparar reportes.

### Sprint 9 | Trabajo de los nueve integrantes

Distribución sugerida para Sprint Planning. No crea responsables fijos de HU: el equipo puede intercambiar tareas manteniendo dependencias, especialidad, capacidad y Sprint Goal. Cada fila cubre 12 h de desarrollo; las 4 h de eventos y 4 h de reserva son comunes.

| Integrante | Épica(s) que coordina | Trabajo sugerido del sprint | Carga |
| :--- | :--- | :--- | :--- |
| Integrante 1 | EP-PLA | HU-06/49: integrar acceso a compartir y controles de privacidad/plan; criterio histórico de OCR refinado el 2026-10-21: OCR local Yape permitido en Free con consentimiento y revisión; conservar alternativas manuales. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 2 | EP-CCO / EP-INF | HU-41: desarrollar estadísticas y comparación Premium; entregar filtros, base cero y etiqueta de periodo parcial. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 3 | EP-MET / EP-PRE | HU-06/49: desarrollar exportación/restore UI y revisión de comprobante; entregar advertencias de eliminación de cuenta y datos no enviados. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 4 | EP-MOV | HU-49/41: implementar dedupe multifuente y conciliación de reportes; entregar dos compras iguales distintas y reembolso histórico. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 5 | EP-DEU | HU-06/49: persistir referencias de origen y exportación CSV/JSON; entregar restauración que conserva revisiones sin duplicados. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 6 | EP-CTA | HU-06: completar restore de datos y eliminación autenticada de cuenta; entregar aislamiento, pendientes y resultado reintentable sin falso éxito. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 7 | EP-REC / EP-NOT | HU-49: implementar Share Intent y permisos temporales de URI; entregar rechazo de formato y captura notificada de Yape compatible. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 8 | EP-AUT | HU-49: implementar OCR local y parser de comprobante; entregar imagen borrosa, fecha ausente y ambos órdenes de dedupe. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 9 | EP-APS | HU-06/41/49: programar E2E de dos dispositivos, OCR y reportes; entregar comparación contra golden dataset y limpieza de temporales. | 12 h dev + 4 h eventos + 4 h reserva |

### Sprint 9 | Integración, pruebas y Review

**Responsables de épica involucrados:** EP-APS: Integrante 9, EP-INF: Integrante 2, EP-AUT: Integrante 8.

**Spec Kit del Sprint 9: entrega del Responsable de Épica**

- **ACTUALIZAR** - EP-APS | Acceso, Perfil y Seguridad | Integrante 9: `specs/001-ep-aps-acceso-perfil-seguridad/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **ACTUALIZAR** - EP-INF | Inicio e Información Financiera | Integrante 2: `specs/009-ep-inf-informacion-financiera/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.
- **ACTUALIZAR** - EP-AUT | Captura Automática e Import Inbox | Integrante 8: `specs/011-ep-aut-captura-inbox/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.

El Responsable de Épica coordina este artefacto, pero el equipo puede colaborar en su redacción y evidencias. La implementación puede repartirse libremente durante Sprint Planning/Daily; el spec mantiene la fuente de verdad funcional de la épica.

Cada integrante descompone sus 12 h de desarrollo en implementación, pruebas del componente e integración/revisión. Las HU se aceptan solo cuando sus criterios Gherkin y dependencias bloqueantes están satisfechos. Revisar regresiones de reglas compartidas, RLS, dinero, sincronización y acceso por plan cuando el sprint toque esos componentes.

**Guion de Review:** 5 min de objetivo e historias; 25 min de demo con al menos un error/recuperación; 10 min de comprobación de criterios y dependencias; 5 min de feedback y pendientes. No mostrar solo pantallas: demostrar persistencia, efectos y reglas.

**Definition of Done:** integrado, revisado, pruebas verdes, datos coherentes, sin bloqueantes, estados/offline/privacidad cubiertos y trazabilidad Épica↔HU↔dependencias actualizada. Una HU parcial no suma puntos aceptados; spec.md de cada épica involucrada creado/actualizado, consistente con las HU, reglas, dependencias y evidencia del sprint.

---

## Sprint 10 | 11/11/2026 - 17/11/2026

**Sprint Goal:** Kipu entrega una beta integrada y verificable.

**Entrega completa / finalidad (MVP 10):** Al cierre debe quedar una beta integrada y verificable de Kipu: Mi plan y transición a Lifetime correctamente auditados, reglas de renovación verificadas, recorrido acumulativo de las funcionalidades comprometidas, regresión final sin bloqueantes críticos, APK identificable mediante hash y documentación explícita de limitaciones reales de fuentes e integraciones.

**Capacidad:** 9 integrantes x 20 h = 180 h; 108 h de desarrollo comprometido, 36 h de eventos y 36 h de reserva.

| HU | Bloqueantes | Épica / resp. | Pts |
| :--- | :--- | :--- | :--- |
| HU-60 Mi plan, Lifetime y auditoría | HU-53, HU-54, HU-55, HU-56, HU-58, HU-59 | EP-PLA / I1 | 8 |

*Total estimado: 8 puntos. Los puntos son esfuerzo relativo; no equivalen a horas.*

### Orden recomendado por dependencias

**Primero:** HU-60 (Mi plan, Lifetime y auditoría)

**Entrada:** incremento S9 integrado y sus pruebas verdes; accesos de proveedor necesarios confirmados.

**Riesgo y tratamiento:** Defectos de última hora: priorizar integridad, aislamiento y compras; no llamar beta completa a un build con bloqueantes.

**Qué se demuestra:** Abrir Mi plan y comprobar bloqueo de Lifetime mientras siga renovación recurrente. Verificar no renovación y completar compra de test Lifetime. Ejecutar recorrido acumulativo y mostrar informe de regresión, hash del APK y limitaciones reales de fuentes.

### Sprint 10 | Trabajo de los nueve integrantes

Distribución sugerida para Sprint Planning. No crea responsables fijos de HU: el equipo puede intercambiar tareas manteniendo dependencias, especialidad, capacidad y Sprint Goal. Cada fila cubre 12 h de desarrollo; las 4 h de eventos y 4 h de reserva son comunes.

| Integrante | Épica(s) que coordina | Trabajo sugerido del sprint | Carga |
| :--- | :--- | :--- | :--- |
| Integrante 1 | EP-PLA | HU-60: completar Mi plan y transición Lifetime; entregar no renovación verificada y rutas de gestión/restauración. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 2 | EP-CCO / EP-INF | HU-39/41: corregir defectos priorizados de Inicio/reportes y fuente ampliada; entregar capturas finales y navegación sin recortes. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 3 | EP-MET / EP-PRE | HU-26/30/38: corregir CRUD de planificación según regresión; entregar cero defectos bloqueantes de cierre/eliminación. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 4 | EP-MOV | HU-60/19/31: auditar permisos y matemáticas, corregir defectos; entregar invariantes financieras aprobadas por casos automatizados. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 5 | EP-DEU | HU-06/21: probar y reparar migraciones y restauración desde versiones previas; entregar checksum y conteos consistentes del dataset. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 6 | EP-CTA | HU-60/55: verificar restricciones remotas, auditoría y eventos tardíos; entregar reporte de aislamiento y rechazo de bypass. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 7 | EP-REC / EP-NOT | HU-45/44: reparar consumo de batería, avisos y revocación en dispositivos reales; entregar matriz de compatibilidad declarada. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 8 | EP-AUT | HU-49/50: corregir parsers de fuentes acordadas y limpiar evidencia sensible; entregar cobertura real y limitaciones documentadas. | 12 h dev + 4 h eventos + 4 h reserva |
| Integrante 9 | EP-APS | Todas las HU: desarrollar automatización de release y regresión final, generar APK; entregar hash, informe TC-01 a TC-16 y guion de demo. | 12 h dev + 4 h eventos + 4 h reserva |

### Sprint 10 | Integración, pruebas y Review

**Responsables de épica involucrados:** EP-PLA: Integrante 1.

**Spec Kit del Sprint 10: entrega del Responsable de Épica**

- **ACTUALIZAR** - EP-PLA | Planes, Límites y Monetización Freemium | Integrante 1: `specs/012-ep-pla-planes-monetizacion/spec.md`. Debe quedar alineado con las HU, reglas y dependencias de este sprint antes de la Review.

El Responsable de Épica coordina este artefacto, pero el equipo puede colaborar en su redacción y evidencias. La implementación puede repartirse libremente durante Sprint Planning/Daily; el spec mantiene la fuente de verdad funcional de la épica.

Cada integrante descompone sus 12 h de desarrollo en implementación, pruebas del componente e integración/revisión. Las HU se aceptan solo cuando sus criterios Gherkin y dependencias bloqueantes están satisfechos. Revisar regresiones de reglas compartidas, RLS, dinero, sincronización y acceso por plan cuando el sprint toque esos componentes.

**Guion de Review:** 5 min de objetivo e historias; 25 min de demo con al menos un error/recuperación; 10 min de comprobación de criterios y dependencias; 5 min de feedback y pendientes. No mostrar solo pantallas: demostrar persistencia, efectos y reglas.

**Definition of Done:** integrado, revisado, pruebas verdes, datos coherentes, sin bloqueantes, estados/offline/privacidad cubiertos y trazabilidad Épica↔HU↔dependencias actualizada. Una HU parcial no suma puntos aceptados; spec.md de cada épica involucrada creado/actualizado, consistente con las HU, reglas, dependencias y evidencia del sprint.

---

## 4. Mapa completo de dependencias

| HU | Resultado | Bloqueantes | Parciales | Relacionadas | Cierre |
| :--- | :--- | :--- | :--- | :--- | :--- |
| HU-01 | Registro e inicio de sesión | Ninguna | Ninguna | Ninguna | S1 |
| HU-02 | Recuperación y ciclo de sesión | HU-01 | Ninguna | Ninguna | S1 |
| HU-03 | Desbloqueo local con biometría | HU-01 | Ninguna | HU-02 | S1 |
| HU-04 | Preferencias y privacidad de saldos | HU-01 | Ninguna | Ninguna | S1 |
| HU-05 | Permisos y fuentes de automatización | HU-01 | HU-52, HU-58 | HU-45 | S1 |
| HU-06 | Portabilidad, eliminación de cuenta y restauración | HU-01, HU-07, HU-18 | HU-19, HU-55 | HU-59 | S9 |
| HU-07 | Administración de cuentas | HU-01, HU-57 | Ninguna | HU-19 | S2 |
| HU-08 | Registro y vínculo de tarjetas | HU-07, HU-57 | Ninguna | HU-09, HU-13 | S2 |
| HU-09 | Línea y saldos de crédito | HU-08, HU-18, HU-19 | Ninguna | HU-10, HU-12, HU-13 | S3 |
| HU-10 | Umbrales de crédito | HU-09 | HU-42 | HU-44 | S3 |
| HU-11 | Catálogo y tasas referenciales | HU-08 | Ninguna | HU-09, HU-13 | S3 |
| HU-12 | Pago de tarjeta | HU-07, HU-09, HU-18, HU-19 | Ninguna | HU-13 | S3 |
| HU-13 | Compras con crédito y cuotas | HU-08, HU-18, HU-19, HU-23 | HU-11 | HU-48 | S3 |
| HU-14 | Categorías y subcategorías | HU-01, HU-57 | Ninguna | HU-17, HU-36 | S2 |
| HU-15 | Comercios y servicios | HU-01 | HU-18 | HU-16, HU-17 | S2 |
| HU-16 | Texto original y alias de comercio | HU-15 | Ninguna | HU-46, HU-49, HU-50 | S5 |
| HU-17 | Preferencias personales de categoría | HU-14, HU-15 | HU-16 | HU-50 | S5 |
| HU-18 | Registro manual de movimientos | HU-07, HU-14 | HU-15 | HU-19, HU-23 | S2 |
| HU-19 | Saldos inmediatos y atomicidad | HU-18 | Ninguna | HU-20, HU-21, HU-25 | S2 |
| HU-20 | Edición de movimientos | HU-18, HU-19 | Ninguna | HU-21, HU-22 | S4 |
| HU-21 | Anulación y borrado lógico | HU-18, HU-19 | Ninguna | HU-20, HU-25 | S4 |
| HU-22 | Búsqueda y filtros | HU-18 | Ninguna | HU-39, HU-41 | S4 |
| HU-23 | Detección de duplicados | HU-18 | Ninguna | HU-46, HU-49 | S2 |
| HU-24 | Aplicar movimientos a obligaciones y metas | HU-18, HU-19, HU-26, HU-27, HU-30, HU-31 | Ninguna | HU-28, HU-32 | S6 |
| HU-25 | Reembolsos vinculados | HU-18, HU-19, HU-36 | Ninguna | HU-37, HU-41 | S7 |
| HU-26 | Deudas propias: creación y administración | HU-07, HU-19, HU-57 | Ninguna | HU-28, HU-29 | S5 |
| HU-27 | Dinero prestado: creación y administración | HU-07, HU-19, HU-57 | Ninguna | HU-28, HU-29 | S5 |
| HU-28 | Pagos y cobros de principal | HU-18, HU-19, HU-26, HU-27 | Ninguna | HU-24, HU-29 | S5 |
| HU-29 | Cuotas, vencimientos y cierre de deudas | HU-26, HU-27, HU-28 | HU-42 | HU-40, HU-44 | S5 |
| HU-30 | Creación y edición de metas | HU-57 | HU-07 | HU-31, HU-32 | S6 |
| HU-31 | Aportes y reservas de metas | HU-18, HU-19, HU-30 | HU-07 | HU-24, HU-32 | S6 |
| HU-32 | Liberación, finalización y archivo de metas | HU-30, HU-31 | Ninguna | HU-24 | S6 |
| HU-33 | Registro de recurrencias | HU-18 | Ninguna | HU-34, HU-35, HU-44 | S6 |
| HU-34 | Pagos esperados y coincidencia con captura | HU-33, HU-46, HU-51 | HU-47 | HU-44, HU-49 | S8 |
| HU-35 | Estados de recurrencias | HU-33 | Ninguna | HU-44 | S6 |
| HU-36 | Crear y consultar presupuestos | HU-14, HU-18, HU-57 | Ninguna | HU-37, HU-38, HU-43 | S7 |
| HU-37 | Renovación, consumo jerárquico y alertas | HU-36, HU-25, HU-42 | Ninguna | HU-43 | S7 |
| HU-38 | Edición, finalización y eliminación de presupuestos | HU-36, HU-37 | Ninguna | Ninguna | S7 |
| HU-39 | Inicio e historial financiero | HU-07, HU-18, HU-19 | HU-22 | HU-41 | S7 |
| HU-40 | Calendario financiero | HU-09, HU-29, HU-33 | Ninguna | HU-44 | S7 |
| HU-41 | Estadísticas y analítica financiera | HU-39, HU-36, HU-09, HU-26, HU-30, HU-58 | HU-22 | HU-25 | S9 |
| HU-42 | Centro de notificaciones | HU-01 | Ninguna | HU-10, HU-43, HU-44 | S3 |
| HU-43 | Alertas presupuestarias y de revisión | HU-42, HU-37, HU-51 | Ninguna | HU-10 | S8 |
| HU-44 | Recordatorios y deduplicación | HU-42, HU-09, HU-29, HU-33 | Ninguna | HU-34, HU-35, HU-40 | S6 |
| HU-45 | Activación del listener | HU-05, HU-58, HU-59 | Ninguna | HU-46, HU-49 | S8 |
| HU-46 | Extracción y candidato | HU-45 | HU-15, HU-16 | HU-47, HU-51 | S8 |
| HU-47 | Confianza y autorregistro | HU-46, HU-18, HU-23 | HU-51 | HU-50 | S8 |
| HU-48 | Confirmación de crédito detectado | HU-46, HU-08, HU-13 | Ninguna | HU-47 | S8 |
| HU-49 | Yape híbrido, OCR y deduplicación | HU-45, HU-46, HU-23, HU-58, HU-59 | HU-51, HU-16 | HU-34, HU-50 | S9 |
| HU-50 | Aprendizaje desde correcciones | HU-16, HU-17, HU-46, HU-51 | Ninguna | HU-47, HU-49 | S8 |
| HU-51 | Bandeja y evidencia mínima | HU-46 | Ninguna | HU-47, HU-43, HU-50 | S8 |
| HU-52 | Elegir Free o prueba Premium | HU-01 | Ninguna | HU-53, HU-57, HU-58 | S1 |
| HU-53 | Compra mensual, anual y Lifetime | HU-52 | HU-54 | HU-56, HU-60 | S3 |
| HU-54 | Verificación y reconocimiento de compras | HU-01, HU-52 | HU-53 | HU-55, HU-56, HU-59, HU-60 | S3 |
| HU-55 | Reconciliación y restauración de compras | HU-54, HU-56 | Ninguna | HU-06, HU-60 | S5 |
| HU-56 | Estados de compra y cancelación | HU-54 | Ninguna | HU-53, HU-55, HU-60 | S3 |
| HU-57 | Cupos, selección y downgrade | HU-01, HU-52 | Ninguna | HU-07, HU-14, HU-26, HU-30, HU-36 | S2 |
| HU-58 | Acceso Free y Premium por capacidad | HU-52, HU-57 | HU-54 | HU-45, HU-49, HU-59 | S4 |
| HU-59 | Concesión offline y expiración | HU-54, HU-58 | Ninguna | HU-05, HU-45, HU-49, HU-60 | S4 |
| HU-60 | Mi plan, Lifetime y auditoría | HU-53, HU-54, HU-55, HU-56, HU-58, HU-59 | Ninguna | HU-06 | S10 |

---

## 5. Reglas de actualización del cronograma

- Cambiar quién implementa una tarea no exige cambiar la documentación si se conserva el Responsable de Épica y la trazabilidad.
- Mover una HU de sprint sí exige revisar todas sus dependencias y actualizar ambos documentos.
- Una HU no se considera terminada si una dependencia Bloqueante sigue incompleta, aunque su UI esté lista.
- Las dependencias Parciales permiten trabajo paralelo, pero deben integrarse antes de aceptación.
- Toda variación de alcance, reglas o dependencias debe actualizar la ficha de HU, el spec.md de la épica, la matriz de dependencias y este cronograma.
- El Product Owner prioriza; Developers seleccionan cómo realizar el trabajo; el Responsable de Épica coordina coherencia del dominio.

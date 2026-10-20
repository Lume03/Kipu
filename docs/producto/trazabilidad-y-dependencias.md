# KIPU V4.2 - Trazabilidad y Dependencias

> **Integración documental — 2026-10-20.** Se conserva el mapa base de `KipuApp/Kipu md/04_Kipu_V4.2_Trazabilidad_y_Dependencias.md`. Los sprints indican planificación, no aceptación ni despliegue. Ver [backlog](backlog.md), [cronograma](../planificacion/cronograma-scrum.md), [artefactos por épica](../../specs/) y [control documental](../control-documental.md). El número de carpeta de una épica no determina su orden de negocio: EP-DEU existe como `013-ep-deu-deudas-prestamos`.

> **Mapa operativo Épica → HU → dependencias → Sprint. No duplica un catálogo de requisitos: la especificación funcional vive en las HU y sus reglas de negocio.**

| Base | Decisión |
| :--- | :--- |
| **Alcance** | Kipu V4.2 \| 12 épicas \| 60 Historias de Usuario \| 10 sprints \| 9 integrantes |
| **Fecha de emisión** | 12 septiembre 2026 |
| **Estructura** | Épicas + HU + dependencias + Sprint |
| **Regla metodológica** | La especificación funcional vive en las HU y sus reglas de negocio; este mapa gobierna la trazabilidad operativa y el orden de integración |

---

## 1. Cómo leer las dependencias

- **Bloqueante:** debe estar disponible para aceptar completamente la HU dependiente.
- **Parcial:** permite avanzar con contratos, mocks o fixtures, pero la integración final espera esa capacidad.
- **Relacionada:** no bloquea, pero comparte reglas o datos y requiere regresión cruzada.
- **Desbloquea:** HU que utilizan la capacidad como dependencia bloqueante directa.

---

## 2. Responsables de épica

| Épica | Responsable de Épica |
| :--- | :--- |
| **EP-APS - Acceso, Perfil y Seguridad** | Integrante 9 - Desarrollo de pruebas, integración y herramientas |
| **EP-CTA - Cuentas y Tarjetas** | Integrante 6 - Desarrollo backend y sincronización |
| **EP-CCO - Categorías, Subcategorías y Comercios** | Integrante 2 - Desarrollo de interfaces y consultas |
| **EP-MOV - Movimientos y Ledger** | Integrante 4 - Desarrollo de reglas financieras |
| **EP-DEU - Deudas y Préstamos** | Integrante 5 - Desarrollo de persistencia local |
| **EP-MET - Metas y Reservas Virtuales** | Integrante 3 - Desarrollo de planificación y formularios |
| **EP-REC - Recurrencias y Obligaciones** | Integrante 7 - Desarrollo de eventos y avisos |
| **EP-PRE - Presupuestos y Consumos** | Integrante 3 - Desarrollo de planificación y formularios |
| **EP-INF - Inicio e Información Financiera** | Integrante 2 - Desarrollo de interfaces y consultas |
| **EP-NOT - Centro de Notificaciones y Avisos** | Integrante 7 - Desarrollo de eventos y avisos |
| **EP-AUT - Captura Automática e Import Inbox** | Integrante 8 - Desarrollo de captura y normalización |
| **EP-PLA - Planes, Límites y Monetización Freemium** | Integrante 1 - Desarrollo Android y planes |

---

## 3. Secuencia por sprint

- **Sprint 1:** HU-01, HU-02, HU-03, HU-04, HU-05, HU-52
- **Sprint 2:** HU-07, HU-08, HU-14, HU-15, HU-18, HU-19, HU-23, HU-57
- **Sprint 3:** HU-09, HU-10, HU-11, HU-12, HU-13, HU-42, HU-53, HU-54, HU-56
- **Sprint 4:** HU-20, HU-21, HU-22, HU-58, HU-59
- **Sprint 5:** HU-16, HU-17, HU-26, HU-27, HU-28, HU-29, HU-55
- **Sprint 6:** HU-24, HU-30, HU-31, HU-32, HU-33, HU-35, HU-44
- **Sprint 7:** HU-25, HU-36, HU-37, HU-38, HU-39, HU-40
- **Sprint 8:** HU-34, HU-43, HU-45, HU-46, HU-47, HU-48, HU-50, HU-51
- **Sprint 9:** HU-06, HU-41, HU-49
- **Sprint 10:** HU-60

---

## 4. Matriz de trazabilidad HU y dependencias

### EP-APS - Acceso, Perfil y Seguridad

**Responsable de Épica:** Integrante 9 - Desarrollo de pruebas, integración y herramientas

| HU | Sprint | Pts | Bloqueantes | Parciales | Relacionadas | Desbloquea |
| :--- | :---: | :---: | :--- | :--- | :--- | :--- |
| **HU-01 Registro e inicio de sesión** | S1 | 5 | Ninguna | Ninguna | Ninguna | HU-02, HU-03, HU-04, HU-05, HU-06, HU-07, HU-14, HU-15, HU-42, HU-52, HU-54, HU-57 |
| **HU-02 Recuperación y ciclo de sesión** | S1 | 5 | HU-01 | Ninguna | Ninguna | Ninguna |
| **HU-03 Desbloqueo local con biometría** | S1 | 3 | HU-01 | Ninguna | HU-02 | Ninguna |
| **HU-04 Preferencias y privacidad de saldos** | S1 | 3 | HU-01 | Ninguna | Ninguna | Ninguna |
| **HU-05 Permisos y fuentes de automatización** | S1 | 3 | HU-01 | HU-52, HU-58 | HU-45 | HU-45 |
| **HU-06 Portabilidad, eliminación de cuenta y restauración** | S9 | 8 | HU-01, HU-07, HU-18 | HU-19, HU-55 | HU-59 | Ninguna |

---

### EP-CTA - Cuentas y Tarjetas

**Responsable de Épica:** Integrante 6 - Desarrollo backend y sincronización

| HU | Sprint | Pts | Bloqueantes | Parciales | Relacionadas | Desbloquea |
| :--- | :---: | :---: | :--- | :--- | :--- | :--- |
| **HU-07 Administración de cuentas** | S2 | 5 | HU-01, HU-57 | Ninguna | HU-19 | HU-06, HU-08, HU-12, HU-18, HU-26, HU-27, HU-39 |
| **HU-08 Registro y vínculo de tarjetas** | S2 | 5 | HU-07, HU-57 | Ninguna | HU-09, HU-13 | HU-09, HU-11, HU-13, HU-48 |
| **HU-09 Línea y saldos de crédito** | S3 | 8 | HU-08, HU-18, HU-19 | Ninguna | HU-10, HU-12, HU-13 | HU-10, HU-12, HU-40, HU-41, HU-44 |
| **HU-10 Umbrales de crédito** | S3 | 3 | HU-09 | HU-42 | HU-44 | Ninguna |
| **HU-11 Catálogo y tasas referenciales** | S3 | 5 | HU-08 | Ninguna | HU-09, HU-13 | Ninguna |
| **HU-12 Pago de tarjeta** | S3 | 5 | HU-07, HU-09, HU-18, HU-19 | Ninguna | HU-13 | Ninguna |
| **HU-13 Compras con crédito y cuotas** | S3 | 8 | HU-08, HU-18, HU-19, HU-23 | HU-11 | HU-48 | HU-48 |

---

### EP-CCO - Categorías, Subcategorías y Comercios

**Responsable de Épica:** Integrante 2 - Desarrollo de interfaces y consultas

| HU | Sprint | Pts | Bloqueantes | Parciales | Relacionadas | Desbloquea |
| :--- | :---: | :---: | :--- | :--- | :--- | :--- |
| **HU-14 Categorías y subcategorías** | S2 | 8 | HU-01, HU-57 | Ninguna | HU-17, HU-36 | HU-17, HU-18, HU-36 |
| **HU-15 Comercios y servicios** | S2 | 5 | HU-01 | HU-18 | HU-16, HU-17 | HU-16, HU-17 |
| **HU-16 Texto original y alias de comercio** | S5 | 5 | HU-15 | Ninguna | HU-46, HU-49, HU-50 | HU-50 |
| **HU-17 Preferencias personales de categoría** | S5 | 5 | HU-14, HU-15 | HU-16 | HU-50 | HU-50 |

---

### EP-MOV - Movimientos y Ledger

**Responsable de Épica:** Integrante 4 - Desarrollo de reglas financieras

| HU | Sprint | Pts | Bloqueantes | Parciales | Relacionadas | Desbloquea |
| :--- | :---: | :---: | :--- | :--- | :--- | :--- |
| **HU-18 Registro manual de movimientos** | S2 | 8 | HU-07, HU-14 | HU-15 | HU-19, HU-23 | HU-06, HU-09, HU-12, HU-13, HU-19, HU-20, HU-21, HU-22, HU-23, HU-24, HU-25, HU-28, HU-31, HU-33, HU-36, HU-39, HU-47 |
| **HU-19 Saldos inmediatos y atomicidad** | S2 | 8 | HU-18 | Ninguna | HU-20, HU-21, HU-25 | HU-09, HU-12, HU-13, HU-20, HU-21, HU-24, HU-25, HU-26, HU-27, HU-28, HU-31, HU-39 |
| **HU-20 Edición de movimientos** | S4 | 5 | HU-18, HU-19 | Ninguna | HU-21, HU-22 | Ninguna |
| **HU-21 Anulación y borrado lógico** | S4 | 5 | HU-18, HU-19 | Ninguna | HU-20, HU-25 | Ninguna |
| **HU-22 Búsqueda y filtros** | S4 | 5 | HU-18 | Ninguna | HU-39, HU-41 | Ninguna |
| **HU-23 Detección de duplicados** | S2 | 5 | HU-18 | Ninguna | HU-46, HU-49 | HU-13, HU-47, HU-49 |
| **HU-24 Aplicar movimientos a obligaciones y metas** | S6 | 8 | HU-18, HU-19, HU-26, HU-27, HU-30, HU-31 | Ninguna | HU-28, HU-32 | Ninguna |
| **HU-25 Reembolsos vinculados** | S7 | 8 | HU-18, HU-19, HU-36 | Ninguna | HU-37, HU-41 | HU-37 |

---

### EP-DEU - Deudas y Préstamos

**Responsable de Épica:** Integrante 5 - Desarrollo de persistencia local

| HU | Sprint | Pts | Bloqueantes | Parciales | Relacionadas | Desbloquea |
| :--- | :---: | :---: | :--- | :--- | :--- | :--- |
| **HU-26 Deudas propias: creación y administración** | S5 | 8 | HU-07, HU-19, HU-57 | Ninguna | HU-28, HU-29 | HU-24, HU-28, HU-29, HU-41 |
| **HU-27 Dinero prestado: creación y administración** | S5 | 8 | HU-07, HU-19, HU-57 | Ninguna | HU-28, HU-29 | HU-24, HU-28, HU-29 |
| **HU-28 Pagos y cobros de principal** | S5 | 8 | HU-18, HU-19, HU-26, HU-27 | Ninguna | HU-24, HU-29 | HU-29 |
| **HU-29 Cuotas, vencimientos y cierre de deudas** | S5 | 5 | HU-26, HU-27, HU-28 | HU-42 | HU-40, HU-44 | HU-40, HU-44 |

---

### EP-MET - Metas y Reservas Virtuales

**Responsable de Épica:** Integrante 3 - Desarrollo de planificación y formularios

| HU | Sprint | Pts | Bloqueantes | Parciales | Relacionadas | Desbloquea |
| :--- | :---: | :---: | :--- | :--- | :--- | :--- |
| **HU-30 Creación y edición de metas** | S6 | 8 | HU-57 | HU-07 | HU-31, HU-32 | HU-24, HU-31, HU-32, HU-41 |
| **HU-31 Aportes y reservas de metas** | S6 | 8 | HU-18, HU-19, HU-30 | HU-07 | HU-24, HU-32 | HU-24, HU-32 |
| **HU-32 Liberación, finalización y archivo de metas** | S6 | 5 | HU-30, HU-31 | Ninguna | HU-24 | Ninguna |

---

### EP-REC - Recurrencias y Obligaciones

**Responsable de Épica:** Integrante 7 - Desarrollo de eventos y avisos

| HU | Sprint | Pts | Bloqueantes | Parciales | Relacionadas | Desbloquea |
| :--- | :---: | :---: | :--- | :--- | :--- | :--- |
| **HU-33 Registro de recurrencias** | S6 | 8 | HU-18 | Ninguna | HU-34, HU-35, HU-44 | HU-34, HU-35, HU-40, HU-44 |
| **HU-34 Pagos esperados y coincidencia con captura** | S8 | 5 | HU-33, HU-46, HU-51 | HU-47 | HU-44, HU-49 | Ninguna |
| **HU-35 Estados de recurrencias** | S6 | 3 | HU-33 | Ninguna | HU-44 | Ninguna |

---

### EP-PRE - Presupuestos y Consumos

**Responsable de Épica:** Integrante 3 - Desarrollo de planificación y formularios

| HU | Sprint | Pts | Bloqueantes | Parciales | Relacionadas | Desbloquea |
| :--- | :---: | :---: | :--- | :--- | :--- | :--- |
| **HU-36 Crear y consultar presupuestos** | S7 | 8 | HU-14, HU-18, HU-57 | Ninguna | HU-37, HU-38, HU-43 | HU-25, HU-37, HU-38, HU-41 |
| **HU-37 Renovación, consumo jerárquico y alertas** | S7 | 8 | HU-36, HU-25, HU-42 | Ninguna | HU-43 | HU-38, HU-43 |
| **HU-38 Edición, finalización y eliminación de presupuestos** | S7 | 5 | HU-36, HU-37 | Ninguna | Ninguna | Ninguna |

---

### EP-INF - Inicio e Información Financiera

**Responsable de Épica:** Integrante 2 - Desarrollo de interfaces y consultas

| HU | Sprint | Pts | Bloqueantes | Parciales | Relacionadas | Desbloquea |
| :--- | :---: | :---: | :--- | :--- | :--- | :--- |
| **HU-39 Inicio e historial financiero** | S7 | 8 | HU-07, HU-18, HU-19 | HU-22 | HU-41 | HU-41 |
| **HU-40 Calendario financiero** | S7 | 5 | HU-09, HU-29, HU-33 | Ninguna | HU-44 | Ninguna |
| **HU-41 Estadísticas y analítica financiera** | S9 | 8 | HU-39, HU-36, HU-09, HU-26, HU-30, HU-58 | HU-22 | HU-25 | Ninguna |

---

### EP-NOT - Centro de Notificaciones y Avisos

**Responsable de Épica:** Integrante 7 - Desarrollo de eventos y avisos

| HU | Sprint | Pts | Bloqueantes | Parciales | Relacionadas | Desbloquea |
| :--- | :---: | :---: | :--- | :--- | :--- | :--- |
| **HU-42 Centro de notificaciones** | S3 | 5 | HU-01 | Ninguna | HU-10, HU-43, HU-44 | HU-37, HU-43, HU-44 |
| **HU-43 Alertas presupuestarias y de revisión** | S8 | 5 | HU-42, HU-37, HU-51 | Ninguna | HU-10 | Ninguna |
| **HU-44 Recordatorios y deduplicación** | S6 | 5 | HU-42, HU-09, HU-29, HU-33 | Ninguna | HU-34, HU-35, HU-40 | Ninguna |

---

### EP-AUT - Captura Automática e Import Inbox

**Responsable de Épica:** Integrante 8 - Desarrollo de captura y normalización

| HU | Sprint | Pts | Bloqueantes | Parciales | Relacionadas | Desbloquea |
| :--- | :---: | :---: | :--- | :--- | :--- | :--- |
| **HU-45 Activación del listener** | S8 | 5 | HU-05, HU-58, HU-59 | Ninguna | HU-46, HU-49 | HU-46, HU-49 |
| **HU-46 Extracción y candidato** | S8 | 8 | HU-45 | HU-15, HU-16 | HU-47, HU-51 | HU-34, HU-47, HU-48, HU-49, HU-50, HU-51 |
| **HU-47 Confianza y autorregistro** | S8 | 8 | HU-46, HU-18, HU-23 | HU-51 | HU-50 | Ninguna |
| **HU-48 Confirmación de crédito detectado** | S8 | 5 | HU-46, HU-08, HU-13 | Ninguna | HU-47 | Ninguna |
| **HU-49 Yape híbrido, OCR y deduplicación** | S9 | 13 | HU-45, HU-46, HU-23, HU-58, HU-59 | HU-51, HU-16 | HU-34, HU-50 | Ninguna |
| **HU-50 Aprendizaje desde correcciones** | S8 | 8 | HU-16, HU-17, HU-46, HU-51 | Ninguna | HU-47, HU-49 | Ninguna |
| **HU-51 Bandeja y evidencia mínima** | S8 | 5 | HU-46 | Ninguna | HU-47, HU-43, HU-50 | HU-34, HU-43, HU-50 |

---

### EP-PLA - Planes, Límites y Monetización Freemium

**Responsable de Épica:** Integrante 1 - Desarrollo Android y planes

| HU | Sprint | Pts | Bloqueantes | Parciales | Relacionadas | Desbloquea |
| :--- | :---: | :---: | :--- | :--- | :--- | :--- |
| **HU-52 Elegir Free o prueba Premium** | S1 | 5 | HU-01 | Ninguna | HU-53, HU-57, HU-58 | HU-53, HU-54, HU-57, HU-58 |
| **HU-53 Compra mensual, anual y Lifetime** | S3 | 8 | HU-52 | HU-54 | HU-56, HU-60 | HU-60 |
| **HU-54 Verificación y reconocimiento de compras** | S3 | 8 | HU-01, HU-52 | HU-53 | HU-55, HU-56, HU-59, HU-60 | HU-55, HU-56, HU-59, HU-60 |
| **HU-55 Reconciliación y restauración de compras** | S5 | 13 | HU-54, HU-56 | Ninguna | HU-06, HU-60 | HU-60 |
| **HU-56 Estados de compra y cancelación** | S3 | 5 | HU-54 | Ninguna | HU-53, HU-55, HU-60 | HU-55, HU-60 |
| **HU-57 Cupos, selección y downgrade** | S2 | 13 | HU-01, HU-52 | Ninguna | HU-07, HU-14, HU-26, HU-30, HU-36 | HU-07, HU-08, HU-14, HU-26, HU-27, HU-30, HU-36, HU-58 |
| **HU-58 Acceso Free y Premium por capacidad** | S4 | 8 | HU-52, HU-57 | HU-54 | HU-45, HU-49, HU-59 | HU-41, HU-45, HU-49, HU-59, HU-60 |
| **HU-59 Concesión offline y expiración** | S4 | 8 | HU-54, HU-58 | Ninguna | HU-05, HU-45, HU-49, HU-60 | HU-45, HU-49, HU-60 |
| **HU-60 Mi plan, Lifetime y auditoría** | S10 | 8 | HU-53, HU-54, HU-55, HU-56, HU-58, HU-59 | Ninguna | HU-06 | Ninguna |

---

## 5. Regla de actualización

Cuando una HU se divide, cambia de sprint o modifica una dependencia, deben actualizarse en la misma revisión: su ficha del Product Backlog, el spec.md de la épica, esta matriz y el Cronograma Scrum. No se mantiene una segunda lista funcional paralela.
